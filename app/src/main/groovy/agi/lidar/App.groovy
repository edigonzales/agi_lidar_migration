package agi.lidar

import ch.ehi.ili2db.gui.Config
import ch.ehi.ili2h2gis.H2gisMain
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstJNI
import org.gdal.gdal.gdal;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.VectorTranslateOptions;
import org.gdal.gdalconst.gdalconstJNI;
import java.util.Vector;

import geoscript.feature.Feature
import geoscript.layer.GeoTIFF
import geoscript.layer.Layer
import geoscript.layer.Raster
import geoscript.layer.Shapefile
import geoscript.workspace.Directory

import groovy.sql.Sql
import groovy.io.FileType

import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import net.lingala.zip4j.ZipFile


def DOWNLOAD_FOLDER = "/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.contour50cm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.so.agi.lidar_2014.contour50cm/"
def TEMP_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/temp/"
def SHP_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/shp/"
def SHPZIP_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/shpzip/"
def XTF_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/xtf/"
def TEMPLATE_DB_FILE = Paths.get("../data/template_lidar.mv.db").toFile().getAbsolutePath()

gdal.AllRegister()
gdal.UseExceptions()
println("Running against GDAL " + gdal.VersionInfo())


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
//tiles = ["25941218_50cm", "25941219_50cm", "26041231_50cm"]
tiles = ["26061232_50cm"]

for (String tile : tiles) {
    println "Processing: $tile"

    // Unzip zipped Shapefile
//    def zip = new ZipFile(Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile())
//    zip.entries().each {
//        if (!it.isDirectory()) {
//            def fOut = Paths.get(TEMP_FOLDER, it.name).toFile()
//            def fos = new FileOutputStream(fOut)
//            def buf = new byte[it.size]
//            def len = zip.getInputStream(it).read(buf)
//            fos.write(buf, 0, len)
//            fos.close()
//        }
//    }
//    zip.close()
    new ZipFile(Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile().getAbsolutePath()).extractAll(TEMP_FOLDER);


    // Fix corrupt shapefiles
    def srcFileName = Paths.get(TEMP_FOLDER, "contour2014_" + tile + ".shp").toFile().getAbsolutePath()
    def dstFileName = Paths.get(SHP_FOLDER, "contour2014_" + tile + ".shp").toFile().getAbsolutePath()
    Dataset srcDS = gdal.OpenEx(srcFileName, gdalconstJNI.OF_VECTOR_get() | gdalconstJNI.OF_VERBOSE_ERROR_get())

    Vector newArgs = new Vector();
//    newArgs.add("-f")
//    newArgs.add("GPKG")
    newArgs.add("-skipfailures")
    newArgs.add("-f")
    newArgs.add("ESRI Shapefile")
    gdal.VectorTranslate(dstFileName, srcDS, new VectorTranslateOptions(newArgs));
    srcDS.delete()

    def shpZipFileName = Paths.get(SHPZIP_FOLDER, tile + ".zip").toFile()
    ZipOutputStream shpZipFile = new ZipOutputStream(new FileOutputStream(shpZipFileName))
    new File(SHP_FOLDER).eachFile() { file ->
        if (file.isFile() && file.name.contains("contour2014")){
            shpZipFile.putNextEntry(new ZipEntry(file.name))
            def buffer = new byte[file.size()]
            file.withInputStream {
                shpZipFile.write(buffer, 0, it.read(buffer))
            }
            shpZipFile.closeEntry()
        }
    }
    shpZipFile.close()

    // Read features from Shapefile and insert contours into h2gis database.
    Shapefile contours = new Shapefile(Paths.get(SHP_FOLDER, "contour2014_" + tile + ".shp").toFile().getAbsolutePath())
    //Shapefile contours = new Shapefile("/Volumes/Samsung_T5/agi_lidar_migration/temp/gaga/contour2014_26061232_50cm_new.shp")
    println "# Features in Contours = ${contours.count}"

    def dbFileName = Paths.get(SHP_FOLDER, tile + ".mv.db").toFile().getAbsolutePath()
    new File(dbFileName).bytes = new File(TEMPLATE_DB_FILE).bytes
    dbFileName = dbFileName.reverse().drop(6).reverse()
    def db = [url:"jdbc:h2:file:$dbFileName", user:'', password:'', driver:'org.h2.Driver']

    // TODO:
    // - test with batch
    // - use transaction
    Sql.withInstance(db.url, db.user, db.password, db.driver) { sql ->
        try {
            contours.eachFeature { Feature feature ->
                def t_id = sql.firstRow("SELECT next value FOR t_ili2db_seq AS t_id").values().getAt(0)
                def elev = feature["elev"]

                if (feature.geom != null) {
                    def geom = feature.geom.wkt
                    def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_MLineFromText($geom), 2014)"
                    //println insertSql
//                sql.execute(insertSql)

                    println feature["ID"]
                } else {
                    println "empty geometries found"
                }



            }
        } catch (java.nio.BufferUnderflowException e) {
            println "corrupt shapefile: $tile"
        }
    }
//
//    // Export XTF
//    Config settings = new Config();
//    new H2gisMain().initConfig(settings);
//    settings.setFunction(Config.FC_EXPORT)
//    settings.setModels("SO_AGI_Hoehenkurven_Publikation_20210115")
//    settings.setModeldir(Paths.get("..").toFile().getAbsolutePath()+";"+"http://models.geo.admin.ch")
//    settings.setDbfile(new File(dbFileName).getAbsolutePath());
//    settings.setValidation(false);
//    settings.setItfTransferfile(false);
//    settings.setDburl(db.url);
//    def xtfFileName = Paths.get(XTF_FOLDER, tile + ".xtf").toFile().getAbsolutePath()
//    settings.setXtffile(xtfFileName);
//    Ili2db.run(settings, null);
//
//    ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(Paths.get(XTF_FOLDER, tile + ".zip").toFile()))
//    File xtfFile = new File(xtfFileName)
//    zipFile.putNextEntry(new ZipEntry(xtfFile.name))
//    def buffer = new byte[xtfFile.size()]
//    xtfFile.withInputStream {
//        zipFile.write(buffer, 0, it.read(buffer))
//    }
//    zipFile.closeEntry()
//    zipFile.close()
//
//    // Remove unnecessary files
//    new File(TEMP_FOLDER).eachFile (FileType.FILES) { file ->
//        if (file.name.contains('contour2014_') || file.name.contains("cm")) file.delete()
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