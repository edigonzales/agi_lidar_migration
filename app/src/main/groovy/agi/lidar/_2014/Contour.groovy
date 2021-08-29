package agi.lidar._2014

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
import net.lingala.zip4j.ZipFile
import org.locationtech.jts.operation.overlay.OverlayOp

import java.nio.file.Paths

import static groovy.io.FileType.FILES

def PERIMETER = "../data/2014/perimeter.gpkg"
def TINDEX = "../data/2014/tindex.shp"
def DATA_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm/"
def RESULT_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm_gpkg_tmp/"
def TEMP_FOLDER = "/Users/stefan/tmp/geodata/tmp/"
def BUFFER = 50

GeoPackage perimeterWs = new GeoPackage(new File(PERIMETER))
println perimeterWs.layers
Layer perimeter = perimeterWs.get("lidar_2014_dissolved")
Shapefile tindex = new Shapefile(TINDEX)

for (Feature feature: tindex.features) {
    try {
        String location = feature.get("location")
        String tile = location.reverse().substring(4, 17).reverse()

        println "Processing: ${tile}"

        File resultFile = Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile()
        if (resultFile.exists()) {
            println "skipping... "
            continue
        }
        //if (resultFile.exists()) resultFile.delete()

        def geom = feature.geom
        def env = geom.envelope

        def minX = env.getMinX() as int
        def minY = env.getMinY() as int
        def maxX = env.getMaxX() as int
        def maxY = env.getMaxY() as int

        int easting = tile.substring(0,4) as Integer
        int northing = tile.substring(4,8) as Integer

        //println easting + " " + northing

        List<Raster> rasters = []
        for (int i=-1; i<=1; i++) {
            for (int j=-1; j<=1; j++) {
                println "----------"

                String neighbourTile = (easting + i) as String + (northing + j) as String + "_50cm.tif"
                if (neighbourTile.equalsIgnoreCase(tile)) continue

                println neighbourTile
                File file = new File(DATA_FOLDER + neighbourTile)
                if (!file.exists()) continue
                Format format = Format.getFormat(file)
                Raster raster = format.read()
                rasters.add(raster)
            }
        }
        Raster mosaicedRaster = Raster.mosaic(rasters)
        Raster croppedRaster = mosaicedRaster.crop(new Bounds(minX - BUFFER,minY - BUFFER,maxX + BUFFER,maxY + BUFFER, "EPSG:2056"))

        File outFile = new File(TEMP_FOLDER + "input0.tif")
        Format outFormat = Format.getFormat(outFile)
        outFormat.write(croppedRaster)

        // Das geht code-mässig eleganter (z.B. nicht hardcodierter outfile Name etc.).
        // Jedoch führte das mit Jiffle zu Problemem "Otherwise, the weak references get garbage collected too soon".
        // Kann natürlich auch an meinem Code gelegen haben.
        def infile = Paths.get(TEMP_FOLDER, "input0.tif").toFile().getAbsolutePath()
        def outfile = Paths.get(TEMP_FOLDER, "input5.tif").toFile().getAbsolutePath()

        for (int i=0; i<5; i++) {
            //5.times {
            //println it
            //String n = it.toString()
            //String nplus = new Integer(it+1).toString()

            println i
            String n = i as String
            String nplus = new Integer(i+1).toString()

            File file = Paths.get(TEMP_FOLDER, "input"+n+".tif").toFile()
            Format format = Format.getFormat(file)
            Raster raster = format.read()

            MapAlgebra algebra = new MapAlgebra()

            String script = """
// Set option to treat locations outside the source image
// area as null values
options { outside = null; }

values = [];
foreach (dy in -1:1) {
  foreach (dx in -1:1) {
  
      values << src[dx, dy];
  }
}

dest = mean(values);
"""
            println "********" + raster.coverage.renderedImage

            Raster outputSmooth = algebra.calculate(script, [src:raster], outputName: "dest")
            File outFileSmooth = Paths.get(TEMP_FOLDER, "input"+nplus+".tif").toFile()
            Format outFormatSmooth = Format.getFormat(outFileSmooth)
            outFormatSmooth.write(outputSmooth)
        }

        // 3. Höhenkurven
        File file = new File(Paths.get(outfile).toFile().getAbsolutePath())
        Format format = Format.getFormat(file)
        Raster contourRaster = format.read()

        int band = 0
        def interval = 1.0
        boolean simplify = false
        boolean smooth = false
        Layer contours = contourRaster.contours(band, interval, simplify, smooth)
        println contours.features.size()

        Workspace workspace = new Memory()
        Layer clippedContours = workspace.create(contours.schema);
        List<Feature> clippedFeatures = []

        Bounds bounds = new Bounds(minX, minY, maxX, maxY, "EPSG:2056")
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

            org.locationtech.jts.geom.Geometry cg_tmp = OverlayOp.overlayOp(fg, kg, OverlayOp.INTERSECTION)
            org.locationtech.jts.geom.Geometry cg = OverlayOp.overlayOp(cg_tmp, bg, OverlayOp.INTERSECTION)

            if (cg instanceof org.locationtech.jts.geom.MultiLineString) {
                for (int j=0; j<cg.numGeometries; j++) {
                    org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) cg.getGeometryN(j)
                    def uuid = UUID.randomUUID().toString()
                    Feature f = new Feature([
                            value: feat.get("value"),
                            geom: new LineString(lineString)
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
                                geom: new LineString(lineString)
                        ], uuid, schema)
                        clippedFeatures.add(f)
                    } else if (g instanceof org.locationtech.jts.geom.MultiLineString) {
                        for (int l=0; l<g.numGeometries; l++) {
                            org.locationtech.jts.geom.LineString lineString = (org.locationtech.jts.geom.LineString) g.getGeometryN(l)
                            def uuid = UUID.randomUUID().toString()
                            Feature f = new Feature([
                                    value: feat.get("value"),
                                    geom: new LineString(lineString)
                            ], uuid, schema)
                            clippedFeatures.add(f)
                        }
                    }
                }
            } else {
                def uuid = UUID.randomUUID().toString()
                Feature f = new Feature([
                        value: feat.get("value"),
                        geom: new LineString(cg)
                ], uuid, schema)
                clippedFeatures.add(f)
            }
        }

        clippedContours.add(clippedFeatures)

        Workspace geopkg = new GeoPackage(resultFile)
        geopkg.add(clippedContours, tile)
        geopkg.close()

        new ZipFile(Paths.get(RESULT_FOLDER, tile + ".zip").toFile().getAbsolutePath()).addFile(resultFile)
    } catch (Exception e) {
        e.printStackTrace()
        System.err.println(e.getMessage())
    }
    //break
}
