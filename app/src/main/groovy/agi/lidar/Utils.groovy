package agi.lidar

import ch.ehi.ili2db.base.Ili2db
import ch.ehi.ili2db.gui.Config
import ch.ehi.ili2gpkg.GpkgMain
import ch.ehi.ili2h2gis.H2gisMain
import geoscript.feature.Feature
import geoscript.geom.Bounds
import geoscript.layer.Format
import geoscript.layer.Raster
import groovy.transform.Synchronized

import java.nio.file.Paths

class Utils {

    static def fubar() {
        println "fubar"
    }

    @Synchronized
    static def exportToXtf(String dbFileName, String dbUrl, String xtfFileName) {
        Config settings = new Config()
        new H2gisMain().initConfig(settings)
        settings.setFunction(Config.FC_EXPORT)
        settings.setModels("SO_AGI_Hoehenkurven_3D_Publikation_20210115")
        settings.setDbfile(new File(dbFileName).getAbsolutePath())
        settings.setValidation(false)
        settings.setItfTransferfile(false)
        settings.setDburl(dbUrl)
        settings.setXtffile(xtfFileName)
        Ili2db.run(settings, null)
    }

    @Synchronized
    static def importToGpkg(String dbFileName, String dbUrl, String xtfFileName) {
        Config settings = new Config()
        new GpkgMain().initConfig(settings)
        settings.setFunction(Config.FC_IMPORT)
        settings.setModels("SO_AGI_Hoehenkurven_3D_Publikation_20210115")
        settings.setDbfile(new File(dbFileName).getAbsolutePath())
        settings.setValidation(false)
        settings.setItfTransferfile(false)
        settings.setDburl(dbUrl)
        settings.setXtffile(xtfFileName)
        settings.setDoImplicitSchemaImport(true)
        Config.setStrokeArcs(settings, Config.STROKE_ARCS_ENABLE)
        settings.setDefaultSrsCode("2056")
        Ili2db.run(settings, null)

    }

}
