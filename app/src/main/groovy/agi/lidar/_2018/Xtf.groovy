package agi.lidar._2018

import ch.ehi.ili2db.gui.Config
import ch.ehi.ili2h2gis.H2gisMain
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException
import ch.ehi.ili2pg.PgMain;
import geoscript.feature.Feature
import geoscript.geom.Geometry
import geoscript.geom.LineString
import geoscript.geom.Point
import geoscript.layer.Layer
import geoscript.layer.Shapefile
import geoscript.workspace.Directory
import geoscript.workspace.GeoPackage
import groovy.sql.Sql
import groovy.io.FileType

import java.nio.file.Paths

import net.lingala.zip4j.ZipFile

import java.util.stream.Collectors


def DATA_FOLDER = "/Volumes/Samsung_T5/geodata/ch.bl.agi.lidar_2018.contour50cm_gpkg/"
def TEMP_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/2018/temp/"
def XTF_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/2018/xtf/"
def TEMPLATE_DB_FILE = Paths.get("../data/template_lidar_3D.mv.db").toFile().getAbsolutePath()
def MODEL_NAME = "SO_AGI_Hoehenkurven_3D_Publikation_20210115"

// Read (gdal) VRT file to get a list of all tif files.
def vrt = new groovy.xml.XmlParser().parse("../data/2018/dtm.vrt")
def tiles = vrt.VRTRasterBand[0].ComplexSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

// 25941219_50cm
//tiles = ["25941218_50cm", "25941219_50cm", "26041231_50cm"]
tiles = ["2590500_1254000"]

for (String tile : tiles) {
    println "Processing: $tile"

    try {
        new ZipFile(Paths.get(DATA_FOLDER, tile + ".zip").toFile().getAbsolutePath()).extractAll(TEMP_FOLDER);

        // Read features from Geopackage and insert contours into h2gis database.
        GeoPackage workspace = new GeoPackage(Paths.get(TEMP_FOLDER, tile + ".gpkg").toFile().getAbsolutePath())
        println workspace.layers
        Layer contours = workspace.get(tile)
        //println contours.schema
        println "# Features in Contours = ${contours.count}"

        def dbFileName = Paths.get(TEMP_FOLDER, tile + ".mv.db").toFile().getAbsolutePath()
        new File(dbFileName).bytes = new File(TEMPLATE_DB_FILE).bytes
        dbFileName = dbFileName.reverse().drop(6).reverse()
        def h2 = [url:"jdbc:h2:file:$dbFileName", user:'', password:'', driver:'org.h2.Driver']

        Sql.withInstance(h2.url, h2.user, h2.password, h2.driver) { sql ->
            contours.eachFeature { Feature feature ->
                //println feature.toString()
                def elev = feature["value"]
                Geometry geom = feature.the_geom
                if (geom != null) {
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

                            Point startPoint = cleanedLine.getStartPoint()
                            Point endPoint = cleanedLine.getEndPoint()

//                            if ((startPoint.getX() == endPoint.getX()) ||
//                                startPoint.getY() == endPoint.getY()) {
//                                continue
//                            }

                            //def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_LineFromText($cleanedLine.wkt), 2014)"
                            def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_UpdateZ(ST_LineFromText($cleanedLine.wkt), $elev), 2014)"
                            sql.execute(insertSql)
                        }
                    }
                }
            }
        }

        //Export XTF
        Config settingsH2 = new Config();
        new H2gisMain().initConfig(settingsH2);
        settingsH2.setFunction(Config.FC_EXPORT)
        settingsH2.setModels(MODEL_NAME)
        settingsH2.setModeldir(Paths.get("../model").toFile().getAbsolutePath()+";"+"http://models.geo.admin.ch")
        settingsH2.setDbfile(new File(dbFileName).getAbsolutePath());
        settingsH2.setValidation(false);
        settingsH2.setItfTransferfile(false);
        settingsH2.setDburl(h2.url);
        def xtfTempFileName = Paths.get(TEMP_FOLDER, tile + "_tmp.xtf").toFile().getAbsolutePath()
        settingsH2.setXtffile(xtfTempFileName);
        Ili2db.run(settingsH2, null);

        // Import XTF to subdivide
        def pg = [url:"jdbc:postgresql://192.168.33.1:54321/edit", user:'admin', password:'admin', driver:'org.postgresql.Driver']

        Config settingsPg = new Config();
        new PgMain().initConfig(settingsPg);
        settingsPg.setFunction(Config.FC_IMPORT)
        settingsPg.setModels(MODEL_NAME)
        settingsPg.setModeldir(Paths.get("../model").toFile().getAbsolutePath()+";"+"http://models.geo.admin.ch")
        settingsPg.setDbhost("192.168.33.1")
        settingsPg.setDbport("54321")
        settingsPg.setDbdatabase("edit")
        settingsPg.setDbschema("agi_hoehenkurven_2014_i")
        settingsPg.setDbusr(pg.user)
        settingsPg.setDbpwd(pg.password)
        settingsPg.setDburl(pg.url)
        settingsPg.setValidation(false);
        settingsPg.setItfTransferfile(false);
        Config.setStrokeArcs(settingsPg,Config.STROKE_ARCS_ENABLE);
        settingsPg.setDefaultSrsCode("2056")
        //def xtfFileName = Paths.get(XTF_FOLDER, tile + ".xtf").toFile().getAbsolutePath()
        settingsPg.setXtffile(xtfTempFileName);
        Ili2db.run(settingsPg, null);

        Sql.withInstance(pg.url, pg.user, pg.password, pg.driver) { sql ->
            sql.execute("""
INSERT INTO
    agi_hoehenkurven_2014_e.hoehenkurven_hoehenkurve
    (
        kote,
        geometrie,
        jahr
    )
    SELECT
        kote,
        ST_SubDivide(geometrie) AS geometrie,
        jahr
    FROM agi_hoehenkurven_2014_i.hoehenkurven_hoehenkurve
;
""")
            sql.execute("DELETE FROM agi_hoehenkurven_2014_i.hoehenkurven_hoehenkurve;")
        }

        // Export subdivided XTF
        settingsPg.setFunction(Config.FC_EXPORT)
        settingsPg.setDbschema("agi_hoehenkurven_2014_e")
        def xtfFileName = Paths.get(XTF_FOLDER, tile + ".xtf").toFile().getAbsolutePath()
        settingsPg.setXtffile(xtfFileName);
        Ili2db.run(settingsPg, null);

        Sql.withInstance(pg.url, pg.user, pg.password, pg.driver) { sql ->
            sql.execute("DELETE FROM agi_hoehenkurven_2014_e.hoehenkurven_hoehenkurve;")
        }

        def xtfZipFileName = Paths.get(XTF_FOLDER, tile + ".zip")
        new ZipFile(xtfZipFileName.toFile().getAbsolutePath()).addFile(new File(xtfFileName))

        // Remove unnecessary files
        new File(TEMP_FOLDER).eachFile (FileType.FILES) { file ->
            if (file.name.contains("00"))  file.delete()
        }

        workspace.close()
    } catch (Exception e) {
        e.printStackTrace()
        println e.getMessage()
    }

    break
}
