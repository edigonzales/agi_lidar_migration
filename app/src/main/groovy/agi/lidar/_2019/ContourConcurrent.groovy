package agi.lidar._2019

import geoscript.feature.Feature
import geoscript.feature.Schema
import geoscript.geom.Bounds
import geoscript.geom.LineString
import geoscript.layer.Format
import geoscript.layer.Layer
import geoscript.layer.MapAlgebra
import geoscript.layer.Raster
import geoscript.layer.Shapefile
import geoscript.workspace.GeoPackage
import geoscript.workspace.Memory
import geoscript.workspace.Workspace
import groovyx.gpars.GParsPool
import net.lingala.zip4j.ZipFile
import org.locationtech.jts.operation.overlay.OverlayOp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

def USER_HOME = System.getProperty("user.home");
def PERIMETER = "../data/2019/perimeter5m.gpkg"
def TINDEX = "../data/2019/tindex.shp"
def DATA_FOLDER = USER_HOME +  "/tmp/geodata/ch.so.agi.lidar_2019.dtm/"
def RESULT_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2019.dtm.gpkg_tmp/"
def TEMP_FOLDER = USER_HOME + "/tmp/geodata/tmp/"
def BUFFER = 50
def BOUNDARY_BUFFER = 5
def PIXEL_SIZE = 0.25

GeoPackage perimeterWs = new GeoPackage(new File(PERIMETER))
Layer perimeter = perimeterWs.get("perimeter5m")
Shapefile tindex = new Shapefile(TINDEX)

List<Feature> features = tindex.features

GParsPool.withPool(1) {
    features.makeConcurrent()
    features.each {
        File tmpDir =  null;
        try {
            String location = it.get("link")
            String tile = location.reverse().substring(4, 19).reverse()

            File resultFile = Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile()

            if (resultFile.exists()) return
            //if (resultFile.exists()) resultFile.delete()

            println "Processing: ${tile}"

            def geom = it.geom
            def env = geom.envelope

            def minX = env.getMinX() as int - BUFFER
            def minY = env.getMinY() as int - BUFFER
            def maxX = env.getMaxX() as int + BUFFER
            def maxY = env.getMaxY() as int + BUFFER

            int easting = tile.substring(0,7) as Integer
            int northing = tile.substring(8,15) as Integer

            println(easting)
            println(northing)

            int mosaicMinX = minX
            int mosaicMinY = minY
            int mosaicMaxX = maxX
            int mosaicMaxY = maxY
            Bounds mosaicBounds = null

            int minXBufferFix = 0
            int maxXBufferFix = 0
            int minYBufferFix = 0
            int maxYBufferFix = 0

            tmpDir = Files.createTempDirectory(Paths.get(TEMP_FOLDER), "contour").toFile()

            List<Raster> rasters = []
            for (int i=-1; i<=1; i++) {
                for (int j=-1; j<=1; j++) {
                    String neighbourTile = (easting + i*500) as String + "_" + (northing + j*500) as String + ".tif"
                    println("neighbourTile: " + neighbourTile)
                    if (neighbourTile.equalsIgnoreCase(tile)) continue

                    //println neighbourTile
                    File file = new File(DATA_FOLDER + neighbourTile)
                    // Achtung: Abhängig von den vorhandenen Daten im Verzeichnis.
                    // Und nicht etwa von einer Tileindex-Datei oder ähnlich.
                    if (!file.exists()) {
                        //println "tile: " + tile
                        //println "neighbour: " + neighbourTile
                        if (i == -1 && j == 0) {
                            //println "minXBufferFix"
                            minXBufferFix = +BUFFER + BOUNDARY_BUFFER
                        } else if (i == 1 && j == 0) {
                            //println "maxXBufferFix"
                            maxXBufferFix = -BUFFER - BOUNDARY_BUFFER
                        } else if (i == 0 && j == -1) {
                            //println "minYBufferFix"
                            minYBufferFix = +BUFFER + BOUNDARY_BUFFER
                        } else if (i == 0 && j == 1) {
                            //println "maxYBufferFix"
                            maxYBufferFix = -BUFFER - BOUNDARY_BUFFER
                        }
                        continue
                    }
                    Format format = Format.getFormat(file)
                    Raster raster = format.read()
                    rasters.add(raster)

                    if (raster.bounds.minX < mosaicMinX) {
                        mosaicMinX = raster.bounds.minX
                    }
                    if (raster.bounds.minY < mosaicMinY) {
                        mosaicMinY = raster.bounds.minY
                    }
                    if (raster.bounds.maxX > mosaicMaxX) {
                        mosaicMaxX = raster.bounds.maxX
                    }
                    if (raster.bounds.maxY > mosaicMaxY) {
                        mosaicMaxY = raster.bounds.maxY
                    }

                    mosaicBounds = new Bounds(mosaicMinX, mosaicMinY, mosaicMaxX, mosaicMaxY)
                }
            }
            int width = (mosaicMaxX - mosaicMinX) / PIXEL_SIZE
            int height = (mosaicMaxY - mosaicMinY) / PIXEL_SIZE
            def size = [width, height]
            def options = ["size": size, "bounds": mosaicBounds]

            /*synchronized (this) {*/
                Raster mosaicedRaster = Raster.mosaic(options, rasters)
                Raster croppedRaster = mosaicedRaster.crop(new Bounds(minX+minXBufferFix, minY+minYBufferFix, maxX+maxXBufferFix, maxY+maxYBufferFix, "EPSG:2056"))

                File outFile = Paths.get(tmpDir.getAbsolutePath(), "input0.tif").toFile()
                Format outFormat = Format.getFormat(outFile)
                outFormat.write(croppedRaster)
            //}

            /*synchronized (this) {*/
                // Das geht code-mässig eleganter (z.B. nicht hardcodierter outfile Name etc.).
                // Jedoch führte das mit Jiffle zu Problemen "Otherwise, the weak references get garbage collected too soon".
                // Kann natürlich auch an meinem Code gelegen haben.
                def infile = Paths.get(tmpDir.getAbsolutePath(), "input0.tif").toFile().getAbsolutePath()
                def outfile = Paths.get(tmpDir.getAbsolutePath(), "input5.tif").toFile().getAbsolutePath()

            MapAlgebra algebra = new MapAlgebra()

            for (int i=0; i<5; i++) {

                    System.gc()
                    Thread.sleep(2000)

                    String n = i as String
                    String nplus = new Integer(i+1).toString()

                    File file = Paths.get(tmpDir.getAbsolutePath(), "input"+n+".tif").toFile()
                    Format format = Format.getFormat(file)
                    Raster raster = format.read()

                    String script = """
// Set option to treat locations outside the source image
// area as null values
options { outside = null; }

values = [];
foreach (dy in -2:2) {
  foreach (dx in -2:2) {

      values << src[dx, dy];
  }
}

dest = mean(values);
"""
                    println "********" + raster.coverage.renderedImage

                    Raster outputSmooth = algebra.calculate(script, [src:raster], outputName: "dest")
                    File outFileSmooth = Paths.get(tmpDir.getAbsolutePath(), "input"+nplus+".tif").toFile()
                    Format outFormatSmooth = Format.getFormat(outFileSmooth)
                    outFormatSmooth.write(outputSmooth)
                }
            //}

            // 3. Höhenkurven
            File file = new File(Paths.get(outfile).toFile().getAbsolutePath())
            Format format = Format.getFormat(file)
            Raster contourRaster = format.read()

            int band = 0
            def interval = 0.5
            boolean simplify = false
            boolean smooth = false
            Layer contours = contourRaster.contours(band, interval, simplify, smooth)
            println contours.features.size()

            Workspace workspace = new Memory()
            Layer clippedContours = workspace.create(contours.schema)
            List<Feature> clippedFeatures = []

            Bounds bounds = new Bounds(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY(), "EPSG:2056")
            Schema schema = contours.schema
            println schema

            contours.eachFeature { Feature feat ->
                // Scheint ein Bug zu sein: Falls das Intersection-Resultat
                // ein MultiLineString ist, werden die einzelnen LineStrings
                // miteinander verbunden.
                // Ob jetzt die Umwandlung zu JTS und zurück noch notwendig
                // ist, weiss ich nicht. Jedenfalls das Auseinanderpfrimeln
                // braucht es.
                org.locationtech.jts.geom.LineString fg = feat.geom.g
                org.locationtech.jts.geom.MultiPolygon kg = perimeter.features.get(0).geom.g
                org.locationtech.jts.geom.Polygon bg = bounds.geometry.g

                // Erster Verschnitt ist nur noch für die Kacheln nochtwendig, die an einer Ecke an eine nicht
                // vorhanden Kachel grenzen. Könnte man eventuell auch noch beim Raster abfangen.
                org.locationtech.jts.geom.Geometry cg_tmp = OverlayOp.overlayOp(fg, kg, OverlayOp.INTERSECTION)
                org.locationtech.jts.geom.Geometry cg = OverlayOp.overlayOp(cg_tmp, bg, OverlayOp.INTERSECTION)

                if (cg instanceof org.locationtech.jts.geom.MultiLineString) {
                    for (int j=0; j<cg.numGeometries; j++) {
                        org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) cg.getGeometryN(j)
                        def uuid = UUID.randomUUID().toString()
                        Feature f = new Feature([
                                value: feat.get("value"),
                                geom: new LineString(lineString).simplify(0.01)
                        ], uuid, schema)
                        clippedFeatures.add(f)
                    }
                } else if (cg instanceof org.locationtech.jts.geom.Point) {
                    // do nothing
                } else if (cg instanceof org.locationtech.jts.geom.GeometryCollection) {
                    org.locationtech.jts.geom.GeometryCollection collection = (org.locationtech.jts.geom.GeometryCollection) cg
                    for (int k=0; k<collection.numGeometries; k++) {
                        org.locationtech.jts.geom.Geometry g = collection.getGeometryN(k)
                        if (g instanceof org.locationtech.jts.geom.LineString) {
                            org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) g
                            def uuid = UUID.randomUUID().toString()
                            Feature f = new Feature([
                                    value: feat.get("value"),
                                    geom: new LineString(lineString).simplify(0.01)
                            ], uuid, schema)
                            clippedFeatures.add(f)
                        } else if (g instanceof org.locationtech.jts.geom.MultiLineString) {
                            for (int l=0; l<g.numGeometries; l++) {
                                org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) g.getGeometryN(l)
                                def uuid = UUID.randomUUID().toString()
                                Feature f = new Feature([
                                        value: feat.get("value"),
                                        geom: new LineString(lineString).simplify(0.01)
                                ], uuid, schema)
                                clippedFeatures.add(f)
                            }
                        }
                    }
                } else {
                    def uuid = UUID.randomUUID().toString()
                    Feature f = new Feature([
                            value: feat.get("value"),
                            geom: new LineString(cg).simplify(0.01)
                    ], uuid, schema)
                    clippedFeatures.add(f)
                }
            }

            clippedContours.add(clippedFeatures)

            Workspace geopkg = new GeoPackage(resultFile)
            geopkg.add(clippedContours, tile)
            geopkg.close()

            new ZipFile(Paths.get(RESULT_FOLDER, tile + ".zip").toFile().getAbsolutePath()).addFile(resultFile)

            tmpDir.deleteDir()

        } catch (Exception e) {
            tmpDir.deleteDir()

            e.printStackTrace()
            System.err.println(e.getMessage())
        }
    }
}



