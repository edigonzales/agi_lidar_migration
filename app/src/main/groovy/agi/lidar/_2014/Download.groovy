package agi.lidar._2014

import agi.lidar.Utils

import java.nio.file.Paths

def USER_HOME = System.getProperty("user.home");
def DOWNLOAD_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014.dsm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.so.agi.lidar_2014.dom/"

// Read (gdal) VRT file to get a list of all tif files.
def vrt = new groovy.xml.XmlParser().parse("../data/2014/lidar_2014_dtm_50cm.vrt")
def tiles = vrt.VRTRasterBand[0].SimpleSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

tiles.each { tile ->
    println "Downloading ${tile}"

    Paths.get(DOWNLOAD_FOLDER, tile + ".tif").toFile().withOutputStream { out ->
        out << new URL(DOWNLOAD_URL + tile + ".tif").openStream()
    }

    //Utils.fubar()
}