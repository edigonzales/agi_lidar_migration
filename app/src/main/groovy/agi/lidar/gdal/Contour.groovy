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
def TEMP_FOLDER = "/Volumes/Samsung_T5/tmp/"
def BUFFER = 50

Dataset vrtDataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get())
Dataset[] datasetArray = vrtDataset as Dataset[] // Java: {dataset}

Shapefile tindex = new Shapefile(TINDEX)

for (Feature feature: tindex.features) {
    String location = feature.get("location")
    String tile = location.reverse().substring(4,17).reverse()

    if (tile != "26141236_50cm") continue

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

    def infile = Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath()
    def outfile = Paths.get(TEMP_FOLDER, "output.tif").toFile().getAbsolutePath()

    if (new File(infile).exists()) new File(infile).delete()
//    Dataset dataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get());
//    Dataset[] datasetArray = dataset as Dataset[] // Java: {dataset}

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

    5.times {
        println it

        File file = new File(infile)
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
        Raster output = algebra.calculate(script, [src:raster], outputName: "dest")
        File outFile = new File(outfile)
        Format outFormat = Format.getFormat(outFile)
        outFormat.write(output)

        def src = new File(infile)
        def dst = new File(outfile)
        if (src.exists()) src.delete()
        src << dst.bytes
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

    Workspace geopkg = new GeoPackage(Paths.get(TEMP_FOLDER, tile + ".gpkg").toFile())
    geopkg.add(clippedContours, tile)
    geopkg.close()

    println "hallo welt"
}

vrtDataset.delete()
