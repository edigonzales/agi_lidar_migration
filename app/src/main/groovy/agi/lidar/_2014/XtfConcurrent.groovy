package agi.lidar._2014

import agi.lidar.Gpkg2Dxf
import agi.lidar.Utils
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
import groovyx.gpars.GParsPool
import org.locationtech.jts.geom.Coordinate

import java.nio.file.Files
import java.nio.file.Paths

import net.lingala.zip4j.ZipFile

import java.util.stream.Collectors

def USER_HOME = System.getProperty("user.home");
def DATA_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014.dtm.gpkg_tmp/"
def TINDEX = "../data/2014/tindex.shp"
def TEMP_FOLDER = USER_HOME + "/tmp/geodata/tmp/"
def XTF_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014_contour1m.xtf/"
def GPKG_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014_contour1m.gpkg/"
def DXF_FOLDER = USER_HOME + "/tmp/geodata/ch.so.agi.lidar_2014_contour1m.dxf/"
def TEMPLATE_DB_FILE = Paths.get("../data/template_lidar_3D.mv.db").toFile().getAbsolutePath()
def YEAR = 2014

Shapefile tindex = new Shapefile(TINDEX)
List<Feature> features = tindex.features
//List<String> features = ["/Volumes/Samsung_T5/geodata/ch.so.agi.lidar_2014.dtm/25921228_50cm.tif"]

GParsPool.withPool(1) {
    features.makeConcurrent()
    features.each {f ->
        try {
            String location = f.get("location")
            //String location = f
            String tile = location.reverse().substring(9, 17).reverse()

            println "Processing: $tile"

            File tmpDir = Files.createTempDirectory(Paths.get(TEMP_FOLDER), "xtf").toFile()
            new ZipFile(Paths.get(DATA_FOLDER, tile + ".zip").toFile().getAbsolutePath()).extractAll(tmpDir.getAbsolutePath());

            // Read features from Geopackage and insert contours into h2gis database.
            GeoPackage workspace = new GeoPackage(Paths.get(tmpDir.getAbsolutePath(), tile + ".gpkg").toFile().getAbsolutePath())
            println workspace.layers
            Layer contours = workspace.get(tile)
            println "# Features in Contours = ${contours.count}"

            def dbFileName = Paths.get(tmpDir.getAbsolutePath(), tile + ".mv.db").toFile().getAbsolutePath()
            new File(dbFileName).bytes = new File(TEMPLATE_DB_FILE).bytes
            dbFileName = dbFileName.reverse().drop(6).reverse()
            def h2 = [url:"jdbc:h2:file:$dbFileName", user:'', password:'', driver:'org.h2.Driver']

            Sql.withInstance(h2.url, h2.user, h2.password, h2.driver) { sql ->
                contours.eachFeature { Feature feature ->
                    def elev = feature["value"]
                    Geometry geom = feature.the_geom
                    if (geom != null) {
                        for (int i=0; i<geom.numGeometries; i++) {
                            def t_id = sql.firstRow("SELECT next value FOR t_ili2db_seq AS t_id").values().getAt(0)
                            LineString line = geom.getGeometryN(i).reducePrecision("fixed", scale: 1000)

                            // Z-Koordinate wird beim Erstellen des LineString ignoriert.
                            def coords = line.coordinates.collect() {c ->
                                new Point(c.x, c.y)
                            }

                            // Groovy .unique() ist sehr langsam.
                            def cleanedCoords = coords.stream()
                                    .distinct()
                                    .collect(Collectors.toList());

                            if (cleanedCoords.size() > 2) {
                                LineString cleanedLine = new LineString(coords)
                                if (cleanedLine.numPoints > 256) {
                                    List<Point> points = cleanedLine.getPoints()
                                    int j=1
                                    List<Point> pointsBatch = []
                                    for (int k=0; k<points.size();k++) {
                                        Point point = points.get(k)

                                        if (cleanedLine.getEndPoint().equals(point) && k > 0) {
                                            if (pointsBatch.size() > 1) {
                                                pointsBatch.add(point)
                                                LineString splittedLine = new LineString(pointsBatch)
                                                pointsBatch.clear()
                                                j=1
                                                k -= 2
                                                def id = sql.firstRow("SELECT next value FOR t_ili2db_seq AS t_id").values().getAt(0)
                                                def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($id, $elev, ST_UpdateZ(ST_LineFromText($splittedLine.wkt), $elev), $YEAR)"
                                                sql.execute(insertSql)

                                                continue
                                            }
                                        }

                                        // Eigentlich 256. Aber es darf nicht ein einzelner Vertexpunkt übrigbleiben.
                                        // Damit kann und darf keine Geometrie gemacht werden.
                                        if (j==255) {
                                            LineString splittedLine = new LineString(pointsBatch)
                                            pointsBatch.clear()
                                            j=1
                                            k -= 2
                                            def id = sql.firstRow("SELECT next value FOR t_ili2db_seq AS t_id").values().getAt(0)
                                            def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($id, $elev, ST_UpdateZ(ST_LineFromText($splittedLine.wkt), $elev), $YEAR)"
                                            sql.execute(insertSql)

                                            continue
                                        }

                                        pointsBatch.add(point)
                                        j++
                                    }
                                } else {
                                    //def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_LineFromText($cleanedLine.wkt), 2014)"
                                    def insertSql = "INSERT INTO hoehenkurve (t_id, kote, geometrie, jahr) VALUES ($t_id, $elev, ST_UpdateZ(ST_LineFromText($cleanedLine.wkt), $elev), $YEAR)"
                                    sql.execute(insertSql)
                                }
                            }
                        }
                    }
                }
            }

            workspace.close()

            // Export XTF
            String xtfFileName = Paths.get(tmpDir.getAbsolutePath(), tile + ".xtf").toFile().getAbsolutePath()
            Utils.exportToXtf(new File(dbFileName).getAbsolutePath(), h2.url, xtfFileName)
            def xtfZipFileName = Paths.get(XTF_FOLDER, tile + ".xtf.zip")
            new ZipFile(xtfZipFileName.toFile().getAbsolutePath()).addFile(new File(xtfFileName))

            // Import to GPKG
            String gpkgFileName = Paths.get(GPKG_FOLDER, tile + ".gpkg").toFile().getAbsolutePath()
            String dbUrl = "jdbc:sqlite:${gpkgFileName}"
            Utils.importToGpkg(new File(gpkgFileName).getAbsolutePath(), dbUrl, xtfFileName)
            def gpkgZipFileName = Paths.get(GPKG_FOLDER, tile + ".gpkg.zip")
            new ZipFile(gpkgZipFileName.toFile().getAbsolutePath()).addFile(new File(gpkgFileName))

            // Export to DXF
            String dxfFileName = Paths.get(DXF_FOLDER, tile + ".dxf").toFile().getAbsolutePath()
            Gpkg2Dxf gpkg2Dxf = new Gpkg2Dxf()
            gpkg2Dxf.execute(gpkgFileName, dxfFileName)
            def dxfZipFileName = Paths.get(DXF_FOLDER, tile + ".dxf.zip")
            new ZipFile(dxfZipFileName.toFile().getAbsolutePath()).addFile(new File(dxfFileName))

            tmpDir.deleteDir()

        } catch (Exception e) {
            e.printStackTrace()
            println e.getMessage()
        }
    }
}
