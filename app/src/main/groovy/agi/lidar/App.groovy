package agi.lidar

import ch.ehi.ili2db.gui.Config
import ch.ehi.ili2h2gis.H2gisMain
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;

import geoscript.feature.Feature
import geoscript.geom.Geometry
import geoscript.geom.LineString
import geoscript.geom.Point
import geoscript.layer.Shapefile
import geoscript.workspace.Directory

import groovy.sql.Sql
import groovy.io.FileType

import java.nio.file.Paths

import net.lingala.zip4j.ZipFile

import java.util.stream.Collectors


def DOWNLOAD_FOLDER = "/media/stefan/Samsung_T5/geodata/ch.so.agi.lidar_2014.contour50cm/"
def DOWNLOAD_URL = "https://geo.so.ch/geodata/ch.so.agi.lidar_2014.contour50cm/"
def TEMP_FOLDER = "/media/stefan/Samsung_T5/agi_lidar_migration/temp/"
def XTF_FOLDER = "/media/stefan/Samsung_T5/agi_lidar_migration/xtf/"
def TEMPLATE_DB_FILE = Paths.get("../data/template_lidar_3D.mv.db").toFile().getAbsolutePath()
def MODEL_NAME = "SO_AGI_Hoehenkurven_3D_Publikation_20210115"

// Read (gdal) VRT file to get a list of all tif files.
def vrt = new groovy.xml.XmlParser().parse("../data/lidar_2014_dom_50cm.vrt")
def tiles = vrt.VRTRasterBand[0].SimpleSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

// 25941219_50cm
//tiles = ["25941218_50cm", "25941219_50cm", "26041231_50cm"]
//tiles = ["26061232_50cm"]

for (String tile : tiles) {
    println "Processing: $tile"

    try {
        new ZipFile(Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile().getAbsolutePath()).extractAll(TEMP_FOLDER);

        // Read features from Shapefile and insert contours into h2gis database.
        Shapefile contours = new Shapefile(Paths.get(TEMP_FOLDER, "contour2014_" + tile + ".shp").toFile().getAbsolutePath())
        println "# Features in Contours = ${contours.count}"

        def dbFileName = Paths.get(TEMP_FOLDER, tile + ".mv.db").toFile().getAbsolutePath()
        new File(dbFileName).bytes = new File(TEMPLATE_DB_FILE).bytes
        dbFileName = dbFileName.reverse().drop(6).reverse()
        def db = [url:"jdbc:h2:file:$dbFileName", user:'', password:'', driver:'org.h2.Driver']

        // TODO:
        // - test with batch -> Kein signifikanter Unterschied.
        Sql.withInstance(db.url, db.user, db.password, db.driver) { sql ->
            contours.eachFeature { Feature feature ->
                def elev = feature["elev"]
                // elev % 2 == 0
                Geometry geom = feature.geom
                for (int i=0; i<geom.numGeometries; i++) {
                    def t_id = sql.firstRow("SELECT next value FOR t_ili2db_seq AS t_id").values().getAt(0)
                    LineString line = geom.getGeometryN(i).reducePrecision("fixed", scale: 1000)

                    // Z-Koordinate wird beim Erstellen des LineString ignoriert.
                    def coords = line.coordinates.collect() {it ->
                        new Point(it.x, it.y)
                    }

                    // Groovy .unique() ist sehr langsam.
                    def cleanedCoords = coords.stream()
                            .distinct()
                            .collect(Collectors.toList());

                    if (cleanedCoords.size() > 2) {
                        LineString cleanedLine = new LineString(coords)
                        //def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_LineFromText($cleanedLine.wkt), 2014)"
                        def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_UpdateZ(ST_LineFromText($cleanedLine.wkt), $elev), 2014)"
                        sql.execute(insertSql)
                    }
                }
            }
        }

        //Export XTF
        Config settings = new Config();
        new H2gisMain().initConfig(settings);
        settings.setFunction(Config.FC_EXPORT)
        settings.setModels(MODEL_NAME)
        settings.setModeldir(Paths.get("..").toFile().getAbsolutePath()+";"+"http://models.geo.admin.ch")
        settings.setDbfile(new File(dbFileName).getAbsolutePath());
        settings.setValidation(false);
        settings.setItfTransferfile(false);
        settings.setDburl(db.url);
        def xtfFileName = Paths.get(XTF_FOLDER, tile + ".xtf").toFile().getAbsolutePath()
        settings.setXtffile(xtfFileName);
        Ili2db.run(settings, null);

        def xtfZipFileName = Paths.get(XTF_FOLDER, tile + ".zip")
        new ZipFile(xtfZipFileName.toFile().getAbsolutePath()).addFile(new File(xtfFileName))

        // Remove unnecessary files
        new File(TEMP_FOLDER).eachFile (FileType.FILES) { file ->
            if (file.name.contains('contour2014_') || file.name.contains("cm")) file.delete()
        }
    } catch (Exception e) {
        e.printStackTrace()
        println e.getMessage()
    }

}
