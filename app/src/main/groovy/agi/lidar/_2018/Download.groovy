package agi.lidar._2018

import java.nio.file.Paths

def DOWNLOAD_FOLDER = "/Volumes/Samsung_T5/geodata/ch.bl.agi.lidar_2018.dtm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.bl.agi.lidar_2018.dtm/"

// Read (gdal) VRT file to get a list of all tif files.
def vrt = new groovy.xml.XmlParser().parse("../data/2018/dtm.vrt")
def tiles = vrt.VRTRasterBand[0].ComplexSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

tiles.each { tile ->
    println "Downloading ${tile}"
    Paths.get(DOWNLOAD_FOLDER, tile + ".tif").toFile().withOutputStream { out ->
        out << new URL(DOWNLOAD_URL + tile + ".tif").openStream()
    }
}