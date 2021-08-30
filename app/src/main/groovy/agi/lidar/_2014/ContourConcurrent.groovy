package agi.lidar._2014

import geoscript.feature.Feature
import geoscript.layer.Layer
import geoscript.layer.Shapefile
import geoscript.workspace.GeoPackage
import groovyx.gpars.GParsPool

def PERIMETER = "../data/2014/perimeter2.gpkg"
def TINDEX = "../data/2014/tindex.shp"
def DATA_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm/"
def RESULT_FOLDER = "/Users/stefan/tmp/geodata/ch.so.agi.lidar_2014.dtm_gpkg_tmp/"
def TEMP_FOLDER = "/Users/stefan/tmp/geodata/tmp/"
def BUFFER = 50
def BOUNDARY_BUFFER = 5

def list = [1, 2, 3, 4, 5, 6, 7, 8, 9]

GeoPackage perimeterWs = new GeoPackage(new File(PERIMETER))
Layer perimeter = perimeterWs.get("perimeter2")
Shapefile tindex = new Shapefile(TINDEX)

List<Feature> features = tindex.features

GParsPool.withPool(1) {
    //println 'Sequential: '
    //list.each { print it + ',' }
    //println()
    features.makeConcurrent()
    println 'Concurrent: '
    features.each {
        println it.get("location") + ','

        sleep(20)
    }
    println()
}



