package agi.lidar.gdal

import geoscript.feature.Feature
import geoscript.feature.Schema
import geoscript.geom.Bounds
import geoscript.geom.Geometry
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

//Dataset vrtDataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get())

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

//    if (new File(TEMP_FOLDER, "input.tif").exists()) new File(TEMP_FOLDER, "input.tif").delete()
//    Dataset dataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get());
//    Dataset[] datasetArray = dataset as Dataset[] // Java: {dataset}
//
//    Vector<String> options = new Vector<>();
//    options.add("-overwrite")
//    options.add("-s_srs")
//    options.add("epsg:2056")
//    options.add("-t_srs")
//    options.add("epsg:2056")
//    options.add("-te")
//    options.add(new Integer(minX - BUFFER).toString())
//    options.add(new Integer(minY - BUFFER).toString())
//    options.add(new Integer(maxX + BUFFER).toString())
//    options.add(new Integer(maxY + BUFFER).toString())
//    options.add("-tr")
//    options.add("0.5")
//    options.add("0.5")
//    options.add("-wo")
//    options.add("NUM_THREADS=ALL_CPUS")
//    options.add("-r")
//    options.add("bilinear");
//    options.add("-co");
//    options.add("TILED=TRUE");
//    gdal.Warp(Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath(), datasetArray, new WarpOptions(options))
//    dataset.delete()
//
//    def infile = Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath()
//    def outfile = Paths.get(TEMP_FOLDER, "output.tif").toFile().getAbsolutePath()
//
//    5.times {
//        println it
//
//        File file = new File(infile)
//        Format format = Format.getFormat(file)
//        Raster raster = format.read()
//
//        MapAlgebra algebra = new MapAlgebra()
//
//        String script = """
//// Set option to treat locations outside the source image
//// area as null values
//options { outside = null; }
//
//values = [];
//foreach (dy in -1:1) {
//  foreach (dx in -1:1) {
//      values << src[dx, dy];
//  }
//}
//
//dest = mean(values);
//"""
//
//        Raster output = algebra.calculate(script, [src:raster], outputName: "dest")
//        File outFile = new File(outfile)
//        Format outFormat = Format.getFormat(outFile)
//        outFormat.write(output)
//
////        println "transformedRaster Raster Bounds = ${output.bounds}"
////        println "transformedRaster Raster Size = ${output.size[0]}x${output.size[1]}"
////
////        Point transformedPoint = output.getPoint(100,100)
////        println "Geographic location at pixel 1000,1000 is ${transformedPoint}"
////
////        double transformedElevation = output.getValue(transformedPoint)
////        println transformedElevation
//
//        def src = new File(infile)
//        def dst = new File(outfile)
//        if (src.exists()) src.delete()
//        src << dst.bytes
//    }

    File file = new File(Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath())
    Format format = Format.getFormat(file)
    Raster contourRaster = format.read()

    int band = 0
    int interval = 10
    boolean simplify = false
    boolean smooth = false
    Layer contours = contourRaster.contours(band, interval, simplify, smooth)

//    Workspace workspace = new Memory()
//    Layer clippedContours = workspace.create(contours.schema);
//    List<Feature> clippedFeatures = []

    Bounds bounds = new Bounds(minX, minY, maxX, maxY, "EPSG:2056")
    Workspace workspace = new Memory()
    Layer boundsLayer = workspace.create(contours.schema);
    Feature f = new Feature([
            value: 1,
            geom: bounds.geometry
        ], "1", contours.schema)

    boundsLayer.add(f)

    Schema schema = contours.schema

//    contours.eachFeature { Feature feat ->
//        Geometry clipped = feat.geom.intersection(bounds.geometry)
//
//        org.locationtech.jts.geom.Geometry g = clipped.g
//
//        Feature f = new Feature([
//            value: feat.get("value"),
//            geom: clipped
//        ], feat.id, schema)
//        clippedFeatures.add(f)
//    }
//
//    clippedContours.add(clippedFeatures)



//    Workspace geopkg = new GeoPackage(Paths.get(TEMP_FOLDER, tile + ".gpkg").toFile())
//    geopkg.add(clippedContours, tile)
//    geopkg.close() //?

    println "hallo welt"


}

