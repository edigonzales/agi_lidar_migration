package agi.lidar.gdal

import geoscript.feature.Feature
import geoscript.geom.Point
import geoscript.layer.Format
import geoscript.layer.MapAlgebra
import geoscript.layer.Raster
import geoscript.layer.Shapefile

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

    if (new File(TEMP_FOLDER, "input.tif").exists()) new File(TEMP_FOLDER, "input.tif").delete()
    Dataset dataset = gdal.Open(VRT, gdalconstJNI.GA_ReadOnly_get());
    Dataset[] datasetArray = dataset as Dataset[] // Java: {dataset}

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
    gdal.Warp(Paths.get(TEMP_FOLDER, "input0.tif").toFile().getAbsolutePath(), datasetArray, new WarpOptions(options))
    dataset.delete()

//    def infile = Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath()
//    def outfile = Paths.get(TEMP_FOLDER, "output.tif").toFile().getAbsolutePath()

    options.clear()
    options.add("-overwrite")
    options.add("-s_srs")
    options.add("epsg:2056")
    options.add("-t_srs")
    options.add("epsg:2056")
    options.add("-r")
    options.add("cubicspline")
    options.add("-co");
    options.add("TILED=TRUE");

//    def src = new File("/tmp/input.tif")
//    def dst = new File("/tmp/input2.tif")
//    if (dst.exists()) dst.delete()
//    dst << src.bytes

//    gdal.Unlink("/tmp/input.tif")


//
////    Dataset srcDataset = gdal.Open(Paths.get(TEMP_FOLDER, "input.tif").toFile().getAbsolutePath(), gdalconstJNI.GA_ReadOnly_get());
////    Dataset[] srcDatasetArray = srcDataset as Dataset[]
////    println srcDataset.getRasterXSize()



    1.times {
        println it
        String n = it.toString()
        String nplus = new Integer(it as int + 1).toString()
//        String infile = Paths.get(TEMP_FOLDER, "input"+n+".tif").toFile().getAbsolutePath();
//        String outfile = Paths.get(TEMP_FOLDER, "input"+nplus+".tif").toFile().getAbsolutePath();
//
//        Dataset dataset2 = gdal.Open(infile);
//        Dataset[] datasetArray2 = dataset2 as Dataset[]
//        gdal.Warp(outfile, datasetArray2, new WarpOptions(options))
//        dataset2.delete()

        File file = new File(Paths.get(TEMP_FOLDER, "input0.tif").toFile().getAbsolutePath())
        Format format = Format.getFormat(file)
        Raster raster = format.read()
        println "Original Raster Bounds = ${raster.bounds}"
        println "Original Raster Size = ${raster.size[0]}x${raster.size[1]}"

        Point point = raster.getPoint(100,100)
        println "Geographic location at pixel 1000,1000 is ${point}"

        double elevation = raster.getValue(point)
        println elevation

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
        File outFile = new File(Paths.get(TEMP_FOLDER, "input1.tif").toFile().getAbsolutePath())
        Format outFormat = Format.getFormat(outFile)
        outFormat.write(output)

        println "transformedRaster Raster Bounds = ${output.bounds}"
        println "transformedRaster Raster Size = ${output.size[0]}x${output.size[1]}"

        Point transformedPoint = output.getPoint(100,100)
        println "Geographic location at pixel 1000,1000 is ${transformedPoint}"

        double transformedElevation = output.getValue(transformedPoint)
        println transformedElevation

//        Raster tempRaster = raster.transform(
//                scalex: 3, scaley: 1,
//                shearx: 0.0, sheary: 0.0,
//                translatex: 5, translatey: 5,
//                nodata: [255],
//                interpolation: "BICUBIC"
//        )
//        println "tempRaster Raster Size = ${tempRaster.size[0]}x${tempRaster.size[1]}"

//        Raster transformedRaster = tempRaster.transform(
//                scalex: 0.5, scaley: 0.5,
//                shearx: 0.0, sheary: 0.0,
//                translatex: 0, translatey: 0,
//                nodata: [255],
//                interpolation: "NEAREST"
//        )
//
//        println "transformedRaster Raster Bounds = ${transformedRaster.bounds}"
//        println "transformedRaster Raster Size = ${transformedRaster.size[0]}x${transformedRaster.size[1]}"
//
//        Point transformedPoint = transformedRaster.getPoint(100,100)
//        println "Geographic location at pixel 1000,1000 is ${transformedPoint}"
//
//        double transformedElevation = transformedRaster.getValue(transformedPoint)
//        println transformedElevation



//        File outFile = new File(Paths.get(TEMP_FOLDER, "input1.tif").toFile().getAbsolutePath())
//        Format outFormat = Format.getFormat(outFile)
//        outFormat.write(transformedRaster)

//        File tmpFile = new File(Paths.get(TEMP_FOLDER, "input1_tmp.tif").toFile().getAbsolutePath())
//        Format tmpFormat = Format.getFormat(tmpFile)
//        tmpFormat.write(tempRaster)


    }


    // Ohne diesen Befehl werden nur die Daten des
    // ersten GDAL-Prozess auf die Festplatte gespeichert.
    // Die nachfolgenden Prozesse erzeugen nur 1KB grosse
    // TIFF.
    gdal.GDALDestroyDriverManager()


}

