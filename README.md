# agi_lidar_migration

## Create ili2h2gis template db
```
java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_2D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_2D_Publikation_20210115 --schemaimport

java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_3D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --schemaimport
```

## Test import and export
```
java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile /Volumes/Samsung_T5/agi_lidar_migration/unzip/25941218_50cm --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_Publikation_20210115 --export fubar.xtf
```

```
java -jar /Users/stefan/apps/ili2gpkg-4.4.5/ili2gpkg-4.4.5.jar --dbfile fubar.gpkg --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_2D_Publikation_20210115 --doSchemaImport --import /Volumes/Samsung_T5/agi_lidar_migration/xtf/25941218_50cm.xtf
```

## Run and log
```
./gradlew app:run 2>&1 | tee lidar.log
```


## Snippets

Download files:

```
tiles.each {tile ->
    Paths.get(DOWNLOAD_FOLDER, tile + ".zip").toFile().withOutputStream { out ->
        out << new URL(DOWNLOAD_URL + tile + ".zip").openStream()
    }
}
```
