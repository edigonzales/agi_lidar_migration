package agi.lidar.gdal

import geoscript.feature.Feature
import geoscript.feature.Schema
import geoscript.geom.Bounds
import geoscript.geom.Geometry
import geoscript.geom.LineString
import geoscript.geom.MultiLineString
import geoscript.geom.MultiPolygon
import geoscript.geom.Point
import geoscript.layer.Format
import geoscript.layer.Layer
import geoscript.layer.MapAlgebra
import geoscript.layer.Raster
import geoscript.layer.Shapefile
import geoscript.workspace.GeoPackage
import geoscript.workspace.Memory
import geoscript.workspace.Workspace
import org.gdal.gdal.Dataset
import org.gdal.gdal.WarpOptions
import org.gdal.gdal.gdal
import org.gdal.gdalconst.gdalconstJNI
import org.gdal.gdal.gdal
import org.gdal.gdal.Dataset
import org.gdal.gdal.VectorTranslateOptions
import org.gdal.gdal.TranslateOptions
import org.gdal.gdalconst.gdalconstJNI
import org.geotools.geometry.jts.GeometryClipper
import org.locationtech.jts.operation.overlay.OverlayOp

import java.nio.file.Paths
import java.util.Vector

gdal.AllRegister()
gdal.UseExceptions()
println("Running against GDAL " + gdal.VersionInfo())

def VRT = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.dtm/lidar_2014_dtm_50cm.vrt"
def TINDEX = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.dtm/lidar_2014.shp"
def DATA_FOLDER = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.dtm/"
def RESULT_FOLDER = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.contour50cm_gpkg/"
def TEMP_FOLDER = "/Volumes/Samsung_T5/tmp/"
def BUFFER = 50

Dataset vrtDataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get())
Dataset[] datasetArray = vrtDataset as Dataset[] // Java: {dataset}

Shapefile tindex = new Shapefile(TINDEX)

for (Feature feature: tindex.features) {
    try {
        String location = feature.get("location")
        String tile = location.reverse().substring(4,17).reverse()

        if (Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile().exists()) continue
        //if (tile != "26141236_50cm") continue

        def geom = feature.geom
        def env = geom.envelope

        def minX = env.getMinX() as int
        def minY = env.getMinY() as int
        def maxX = env.getMaxX() as int
        def maxY = env.getMaxY() as int

        println minX
        println minY
        println maxX
        println maxY

        def infile = Paths.get(TEMP_FOLDER, "input0.tif").toFile().getAbsolutePath()
        //def outfile = Paths.get(TEMP_FOLDER, "output.tif").toFile().getAbsolutePath()

        if (new File(infile).exists()) new File(infile).delete()

        Vector<String> options = new Vector<>();
        options.add("-overwrite")
        options.add("-s_srs")
        options.add("epsg:2056")
        options.add("-t_srs")
        options.add("epsg:2056")
        options.add("-te")
        options.add(new Integer(minX - BUFFER).toString())
        options.add(new Integer(minY - BUFFER).toString())
        options.add(new Integer(maxX + BUFFER).toString())
        options.add(new Integer(maxY + BUFFER).toString())
        options.add("-tr")
        options.add("0.5")
        options.add("0.5")
        options.add("-wo")
        options.add("NUM_THREADS=ALL_CPUS")
        options.add("-r")
        options.add("bilinear");
        options.add("-co");
        options.add("TILED=TRUE");
        gdal.Warp(Paths.get(infile).toFile().getAbsolutePath(), datasetArray, new WarpOptions(options))

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

            Raster output = algebra.calculate(script, [src:raster], outputName: "dest")
            File outFile = Paths.get(TEMP_FOLDER, "input"+nplus+".tif").toFile()
            Format outFormat = Format.getFormat(outFile)
            outFormat.write(output)

            // Dieser Ansatz gibt Probleme mit Jiffle resp. vielleicht
            // liegt der Hund auch in meinem Code begraben.
            // Jiffle motzt wegen des Images, das carbage collected
            // wurde. Interessanterweise steht im JiffleBuilder-Code
            // etwas dazu "Otherwise, the weak references get garbage collected too soon."
            // Gefühlt scheint mir Java 8 anfälliger als Java 11 zu sein.
            // Es passierte auch nie beim ersten Durchlauf / beim ersten Bild.
            // Darum verdächtige ich schon noch das Rumkopieren.

            // -> Hilft auch nix. Zuerst siehts gut aus, dann häufen sich
            // die Fehler.
            // Umgestellt von 5.times{} nach for()

//            def src = new File(infile)
//            def dst = new File(outfile)
//            if (src.exists()) src.delete()
//            src << dst.bytes
        }

        File file = new File(Paths.get(infile).toFile().getAbsolutePath())
        Format format = Format.getFormat(file)
        Raster contourRaster = format.read()

        int band = 0
        def interval = 0.5
        boolean simplify = false
        boolean smooth = false
        Layer contours = contourRaster.contours(band, interval, simplify, smooth)

        Workspace workspace = new Memory()
        Layer clippedContours = workspace.create(contours.schema);
        List<Feature> clippedFeatures = []

        Bounds bounds = new Bounds(minX, minY, maxX, maxY, "EPSG:2056")
        Schema schema = contours.schema

        contours.eachFeature { Feature feat ->
            // Scheint ein Bug zu sein: Falls das Intersection-Resultat
            // ein MultiLineString ist, werden die einzelnen LineStrings
            // miteinander verbunden.
            // Ob jetzt die Umwandlung zu JTS und zurück noch notwendig
            // ist, weiss ich nicht. Jedenfalls das Auseinanderpfrimeln
            // braucht es.

            // Ist garantiert, dass ein contour-Geometrie immer
            // ein LineString ist?
            org.locationtech.jts.geom.LineString fg = feat.geom.g
            org.locationtech.jts.geom.Polygon bg = bounds.geometry.g
            org.locationtech.jts.geom.Geometry cg = OverlayOp.overlayOp(fg, bg, OverlayOp.INTERSECTION)

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

        if (Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile().exists()) Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile().delete()

        Workspace geopkg = new GeoPackage(Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile())
        geopkg.add(clippedContours, tile)
        geopkg.close()
    } catch (Exception e) {
        e.printStackTrace()
        System.err.println(e.getMessage())
    }
    println "hallo welt"
}

vrtDataset.delete()
