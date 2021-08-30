package agi.lidar._2014

import geoscript.feature.Feature
import geoscript.geom.Bounds
import geoscript.layer.Format
import geoscript.layer.Layer
import geoscript.layer.MapAlgebra
import geoscript.layer.Raster
import geoscript.layer.Shapefile
import geoscript.workspace.GeoPackage
import groovyx.gpars.GParsPool

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

def PERIMETER = "../data/2014/perimeter.gpkg"
def TINDEX = "../data/2014/tindex.shp"
def DATA_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm/"
def RESULT_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm_gpkg_tmp/"
def TEMP_FOLDER = "/Users/stefan/tmp/geodata/tmp/"
def BUFFER = 50
def BOUNDARY_BUFFER = 5

def list = [1, 2, 3, 4, 5, 6, 7, 8, 9]

GeoPackage perimeterWs = new GeoPackage(new File(PERIMETER))
Layer perimeter = perimeterWs.get("perimeter")
Shapefile tindex = new Shapefile(TINDEX)

List<Feature> features = tindex.features

GParsPool.withPool(2) {
    features.makeConcurrent()
    features.each {
        try {
            String location = it.get("location")
            String tile = location.reverse().substring(4, 17).reverse()

            File resultFile = Paths.get(RESULT_FOLDER, tile + ".gpkg").toFile()

            //if (resultFile.exists()) continue
            //if (resultFile.exists()) resultFile.delete()

            println "Processing: ${tile}"

            def geom = it.geom
            def env = geom.envelope

            def minX = env.getMinX() as int - BUFFER
            def minY = env.getMinY() as int - BUFFER
            def maxX = env.getMaxX() as int + BUFFER
            def maxY = env.getMaxY() as int + BUFFER

            int easting = tile.substring(0,4) as Integer
            int northing = tile.substring(4,8) as Integer

            int minXBufferFix = 0
            int maxXBufferFix = 0
            int minYBufferFix = 0
            int maxYBufferFix = 0

            File tmpDir = Files.createTempDirectory(Paths.get(TEMP_FOLDER), "contour").toFile()

            List<Raster> rasters = []
            for (int i=-1; i<=1; i++) {
                for (int j=-1; j<=1; j++) {
                    String neighbourTile = (easting + i) as String + (northing + j) as String + "_50cm.tif"
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
                }
            }
            synchronized (this) {
                Raster mosaicedRaster = Raster.mosaic(rasters)
                Raster croppedRaster = mosaicedRaster.crop(new Bounds(minX+minXBufferFix, minY+minYBufferFix, maxX+maxXBufferFix, maxY+maxYBufferFix, "EPSG:2056"))

                File outFile = Paths.get(tmpDir.getAbsolutePath(), "input0.tif").toFile()
                Format outFormat = Format.getFormat(outFile)
                outFormat.write(croppedRaster)
            }

            synchronized (this) {
                // Das geht code-mässig eleganter (z.B. nicht hardcodierter outfile Name etc.).
                // Jedoch führte das mit Jiffle zu Problemen "Otherwise, the weak references get garbage collected too soon".
                // Kann natürlich auch an meinem Code gelegen haben.
                def infile = Paths.get(tmpDir.getAbsolutePath(), "input0.tif").toFile().getAbsolutePath()
                def outfile = Paths.get(tmpDir.getAbsolutePath(), "input5.tif").toFile().getAbsolutePath()

                for (int i=0; i<5; i++) {
                    String n = i as String
                    String nplus = new Integer(i+1).toString()

                    File file = Paths.get(tmpDir.getAbsolutePath(), "input"+n+".tif").toFile()
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
                    File outFileSmooth = Paths.get(tmpDir.getAbsolutePath(), "input"+nplus+".tif").toFile()
                    Format outFormatSmooth = Format.getFormat(outFileSmooth)
                    outFormatSmooth.write(outputSmooth)
                }
            }



            //tmpDir.deleteDir()



        } catch (Exception e) {
            e.printStackTrace()
            System.err.println(e.getMessage())
        }
    }
    println()
}



