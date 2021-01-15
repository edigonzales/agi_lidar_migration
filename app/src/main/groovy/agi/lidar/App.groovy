package agi.lidar

import geoscript.feature.Feature
import geoscript.layer.GeoTIFF
import geoscript.layer.Layer
import geoscript.layer.Raster
import geoscript.layer.Shapefile
import geoscript.workspace.Directory

import groovy.sql.Sql

import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.zip.ZipFile

def DOWNLOAD_FOLDER = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.contour50cm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.so.agi.lidar_2014.contour50cm/"

//def TILES_FOLDER = "/Volumes/Samsung_T5/alw_futterbaulinie/uncompressed/"
def UNZIP_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/unzip/"

// Read (gdal) VRT file to get a list of all tif files.
//def vrt = new groovy.xml.XmlParser().parse("../data/lidar_2014_dom_50cm.vrt")
//def tiles = vrt.VRTRasterBand[0].SimpleSource.collect { it ->
//    it.SourceFilename.text().reverse().drop(4).reverse()
//}

//// Download files
//tiles.each {tile ->
//    Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile().withOutputStream { out ->
//        out << new URL(DOWNLOAD_URL + tile + ".zip").openStream()
//    }
//}

// 25941219_50cm
tiles = ["25941218_50cm"]

for (String tile : tiles) {
    def zip = new ZipFile(Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile())
    zip.entries().each {
        if (!it.isDirectory()) {
            def fOut = Paths.get(UNZIP_FOLDER, it.name).toFile()
            def fos = new FileOutputStream(fOut)
            def buf = new byte[it.size]
            def len = zip.getInputStream(it).read(buf)
            fos.write(buf, 0, len)
            fos.close()
        }
    }
    zip.close()

    Shapefile contours = new Shapefile(Paths.get(UNZIP_FOLDER, "contour2014_" + tile + ".shp").toFile().getAbsolutePath())
    println "# Features in Contours = ${contours.count}"
    //println "# Schema = ${contours.schema}"

    def db = [url:'jdbc:h2:file:/Users/stefan/sources/agi_lidar_migration/template_lidar', user:'', password:'', driver:'org.h2.Driver']
    def sql = Sql.newInstance(db.url, db.user, db.password, db.driver)
    sql.eachRow('SELECT * FROM t_ili2db_attrname') { resultSet ->
        def first = resultSet.getString(1)
        println first
    }

    sql.close()


//    try {
//        contours.eachFeature { Feature feature ->
//            println feature.get("ID") + "******" + feature["elev"]
//            println feature.geom.wkt
//        }
//    } catch (java.nio.BufferUnderflowException e) {
//        println "corrupt shapefile"
//    }
}





//File file = new File("/Users/stefan/Downloads/26041231_50cm_uncompressed.tif")
//GeoTIFF geotiff = new GeoTIFF(file)
//Raster raster = geotiff.read()
//
//int band = 0
//int interval = 1
//boolean simplify = false
//boolean smooth = true
//Layer contours = raster.contours(band, interval, simplify, smooth)
//Directory workspace = Shapefile.dump(new File("/Users/stefan/Downloads"), contours)