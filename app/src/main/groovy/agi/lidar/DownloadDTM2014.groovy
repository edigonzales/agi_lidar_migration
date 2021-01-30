package agi.lidar

import java.nio.file.Paths

def DOWNLOAD_FOLDER = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.dtm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.so.agi.lidar_2014.dtm/"

// Read (gdal) VRT file to get a list of all tif files.
def vrt = new groovy.xml.XmlParser().parse("../data/lidar_2014_dom_50cm.vrt")
def tiles = vrt.VRTRasterBand[0].SimpleSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

tiles.each {tile ->
    Paths.get(DOWNLOAD_FOLDER, tile + ".tif").toFile().withOutputStream { out ->
        out << new URL(DOWNLOAD_URL + tile + ".tif").openStream()
    }
}