package agi.lidar

import ch.ehi.ili2db.gui.Config
import ch.ehi.ili2db.base.Ili2db
import ch.ehi.ili2pg.PgMain
import ch.ehi.ili2db.base.Ili2dbException

import groovy.sql.Sql
import groovy.io.FileType

import java.nio.file.Paths
import java.util.stream.Collectors

import net.lingala.zip4j.ZipFile

//def XTF_FOLDER = "/media/stefan/Samsung_T5/agi_lidar_migration/xtf/"
def XTF_FOLDER = "/Volumes/Samsung_T5/agi_lidar_migration/xtf/"
def MODEL_NAME = "SO_AGI_Hoehenkurven_3D_Publikation_20210115"

// Read (gdal) VRT file to get a list of all xtf files.
def vrt = new groovy.xml.XmlParser().parse("../data/lidar_2014_dom_50cm.vrt")
def tiles = vrt.VRTRasterBand[0].SimpleSource.collect { it ->
    it.SourceFilename.text().reverse().drop(4).reverse()
}

for (String tile : tiles) {
    println "Processing: $tile"

    try {
        //Export XTF
        Config settings = new Config();
        new PgMain().initConfig(settings);
        settings.setFunction(Config.FC_IMPORT)
        settings.setConfigReadFromDb(true)
        settings.setModels(MODEL_NAME)
        //settings.setBasketHandling(Config.BASKET_HANDLING_READWRITE)
        Config.setStrokeArcs(settings,Config.STROKE_ARCS_ENABLE)
        settings.setModeldir(Paths.get("../model").toFile().getAbsolutePath()+";"+"http://models.geo.admin.ch")
        settings.setValidation(false);
        settings.setItfTransferfile(false);
        //settings.setDatasetName(tile)
        settings.setDbusr("admin")
        settings.setDbpwd("admin")
        settings.setDburl("jdbc:postgresql://localhost:54321/edit");
        settings.setDbschema("agi_hoehenkurven_2014")
        def xtfFileName = Paths.get(XTF_FOLDER, tile + ".xtf").toFile().getAbsolutePath()
        settings.setXtffile(xtfFileName);
        Ili2db.run(settings, null);

    } catch (Exception e) {
        e.printStackTrace()
        println e.getMessage()
    }


    break

}
