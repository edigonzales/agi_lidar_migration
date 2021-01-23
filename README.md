# agi_lidar_migration

## Create XTF

### Create ili2h2gis template db
```
java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_2D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_2D_Publikation_20210115 --schemaimport

java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_3D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --schemaimport
```

### Test import and export
```
java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile /Volumes/Samsung_T5/agi_lidar_migration/unzip/25941218_50cm --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_Publikation_20210115 --export fubar.xtf
```

```
java -jar /Users/stefan/apps/ili2gpkg-4.4.5/ili2gpkg-4.4.5.jar --dbfile fubar.gpkg --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_2D_Publikation_20210115 --doSchemaImport --import /Volumes/Samsung_T5/agi_lidar_migration/xtf/25941218_50cm.xtf
```

### Run and log
```
./gradlew app:run 2>&1 | tee lidar.log
```

## Import XTF

```
docker volume prune

docker-compose build
docker-compose down
docker-compose up
```

```
java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr admin --dbpwd admin --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014 --schemaimport
```

```
java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54322 --dbdatabase pub --dbusr admin --dbpwd admin --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014_pub --schemaimport
```

```
SELECT Count(*) 
FROM agi_hoehenkurven_2014.hoehenkurven_hoehenkurve hh 
WHERE ST_MemSize(geometrie) > 8192;
```
```
CREATE TABLE agi_hoehenkurven_2014_pub.hoehenkurven_hoehenkurve_subdivided (
	t_id int8 NOT NULL DEFAULT nextval('agi_hoehenkurven_2014_pub.t_ili2db_seq'::regclass),
	kote numeric(5,1) NOT NULL,
	geometrie geometry(LINESTRINGZ, 2056) NOT NULL,
	jahr int4 NULL,
	CONSTRAINT hoehenkurven_hoehenkurve_subdivided_pkey PRIMARY KEY (t_id)
);
CREATE INDEX hoehenkurven_hoehenkurve_subdivided_elev_idx ON agi_hoehenkurven_2014_pub.hoehenkurven_hoehenkurve USING btree (kote);
CREATE INDEX hoehenkurven_hoehenkurve_subdivided_geometrie_idx ON agi_hoehenkurven_2014_pub.hoehenkurven_hoehenkurve USING gist (geometrie);


INSERT INTO
	agi_hoehenkurven_2014_pub.hoehenkurven_hoehenkurve_subdivided
	(
		kote,
		geometrie,
		jahr
	)
SELECT 
	kote,
	ST_SubDivide(geometrie) AS geometrie,
	jahr
FROM agi_hoehenkurven_2014_pub.hoehenkurven_hoehenkurve
;
```


```
CREATE INDEX hoehenkurven_hoehenkurve_elev_idx ON agi_hoehenkurven_2014.hoehenkurven_hoehenkurve USING btree (kote);
```

```
export ORG_GRADLE_PROJECT_dbUriEdit=jdbc:postgresql://localhost:54321/edit
export ORG_GRADLE_PROJECT_dbUserEdit=admin
export ORG_GRADLE_PROJECT_dbPwdEdit=admin
export ORG_GRADLE_PROJECT_dbUriPub=jdbc:postgresql://localhost:54322/pub
export ORG_GRADLE_PROJECT_dbUserPub=admin
export ORG_GRADLE_PROJECT_dbPwdPub=admin
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
