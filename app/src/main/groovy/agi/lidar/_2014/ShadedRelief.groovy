package agi.lidar._2014

import geoscript.feature.Feature
import geoscript.layer.Format
import geoscript.layer.Raster
import geoscript.layer.Shapefile

def USER_HOME = System.getProperty("user.home");
def PERIMETER = "../data/2014/perimeter_5m.gpkg"
def TINDEX = "../data/2014/tindex.shp"
def DATA_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014.dsm/"
def RESULT_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014.dsm_hillshade/"
def TEMP_FOLDER = USER_HOME + "/tmp/geodata/tmp/"

Shapefile tindex = new Shapefile(TINDEX)

for (Feature feature: tindex.features) {
    try {
        String location = feature.get("location")
        String tile = location.reverse().substring(4, 17).reverse()

        println "Processing: ${tile}"

        File file = new File(DATA_FOLDER + tile + ".tif")
        Format format = Format.getFormat(file)
        Raster raster = format.read()
        def azimuths = [225, 270, 315, 360]
        azimuths.each { azimuth ->
            Raster shadedReliefRaster = raster.createShadedRelief(['algorithm':'DEFAULT'], 1.0, 55, azimuth)
            File outfile = new File(TEMP_FOLDER+ tile + "_shaded_"+azimuth+".tif")
            Format outformat = Format.getFormat(outfile)
            outformat.write(shadedReliefRaster)
        }

    } catch (Exception e) {
        e.printStackTrace()
        System.err.println(e.getMessage())
    }

    break

}
