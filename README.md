# agi_lidar_migration

## TODO
- Dokumentation: Perimeterrand ist kleiner, damit die unschönen Randartefakte verschwinden.

## Links
- https://github.com/edigonzales-archiv/av_lidar_produkte/blob/master/contour/contour.py

## Abhängigkeiten
Damit 32bit Tiff mit Deflate-Komprimierung (predictor=2) gelesen werden können, muss mindestens Geoscript Groovy >= 1.18 verwendet werden (resp. die von Gescript verwendete Geotools-Version). Momentan gibt es noch keinen Release und man muss die SNAPSHOT-Version selber kompilieren:

```
mvn clean install -DskipTests
```

## Datenbank
```
mkdir -m 0777 ~/pgdata_lidar

docker run -p 54321:5432 -v ~/pgdata_lidar:/var/lib/postgresql/data:delegated -e POSTGRES_DB=edit -e POSTGRES_PASSWORD=mysecretpassword edigonzales/postgis:13-3.1
```

### Subdivide-Schemas
```
java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr gretl --dbpwd gretl --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014_i --schemaimport

java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr gretl --dbpwd gretl --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014_e --schemaimport
```


## Funktionen

### Download
Download der Kacheln von geo.so.ch/geodata.

### Contour
Berechnung der Höhenlinien. 

Weil Kacheln am Perimeterrand auf wenige Pixel sehr grosse Höhendifferenzen aufweisen (z.B. 1000m -> 0m) entstehen in diesen Regionen sehr viele Höhenkurven. Das kann zu technischen Problemen führen, sie sind visuell hässlich und ohne Aussagekraft. Um dem Vorzubeugen werden die Kacheln bereits vor dem Berechnen der Höhenkurven an der betroffenen Kante verkleinert. Übrig bleibt ein kleines Quadrat bei Kacheln, die sich nicht an einer Kante berühren, sondern nur an einem Punkt. Diese Artefakte werden durch einen Verschnitt mit einem Perimeter-Vektorlayer nach der Berechnung der Höhenkurven entfernt. Den zweiten Schritt könnte man wohl auch noch mit dem ersten Schritt durchführen. Aus Gründen der Klarheit/Einfachheit und der Performance. Die Berechnung dieser "falschen" Höhenkurven ist relativ zeitaufändig.

Damit kachelweise gerechnet werden kann und die Höhenkurven an den Kachelgrenzen perfekt zusammenpassen, kann nicht nur eine einzelne Kachel prozessiert werden, sondern es muss ein Buffer berücksichtigt werden (z.B. 50m). D.h. nach der Berechnung der Höhenkurven müssen diese auf die ursprüngliche Kachel zurückgeschnitten werden. Hier scheint es einen Bug mit Multilinestrings zu geben. Dies führt zu ganz komischen Verknüpfungen/Verbindungen zwischen Geometrien. Es wird über jedes Feature iteriert und jede Geometrie speziell behandelt (auch Geometrycollection und Punkte). Momentan werden die Geoscript Geometrien nach JTS-Geometrien umgewandelt. Das dürfte unnötig sein (Relikt des Bughuntings).

Das Herstellen des Mosaic hat einen Bug, welcher dazu führt, dass die Ausdehnung (width, height) des Mosaics falsch ist. Aus diesem Grund wird die Ausdehnung selber berechnet und als Parameter der Methode übergeben.

## Create XTF

```
java -jar /Users/stefan/apps/ili2gpkg-4.5.0/ili2gpkg-4.5.0.jar --dbfile fubar.gpkg --disableValidation --strokeArcs --defaultSrsCode 2056 --modeldir "../../../sources/agi_lidar_migration/model;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --doSchemaImport --import 25921228_50cm_tmp.xtf
java -jar /Users/stefan/apps/ili2pg-4.5.0/ili2pg-4.5.0.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbschema agi_hoehenkurven_2014 --dbusr gretl --dbpwd gretl  --disableValidation --strokeArcs --defaultSrsCode 2056 --modeldir "../../../sources/agi_lidar_migration/model;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --doSchemaImport --import 25921228_50cm_tmp.xtf
```

### Create ili2h2gis template db
```
java -jar /Users/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_2D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_2D_Publikation_20210115 --schemaimport

java -jar /home/stefan/apps/ili2h2gis-4.4.5/ili2h2gis-4.4.5.jar --dbfile template_lidar_3D --strokeArcs --defaultSrsCode 2056 --disableValidation --modeldir ".;http://models.geo.admin.ch" --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --schemaimport
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
./gradlew -Dorg.gradle.jvmargs=-Xmx2G app:run 2>&1 | tee lidar.log
```

### ili2pg import with subdivide
```
java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr admin --dbpwd admin --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014_i --schemaimport
java -jar /Users/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr admin --dbpwd admin --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014_e --schemaimport
```

## Import XTF

```
docker volume prune

docker-compose build
docker-compose down
docker-compose up
```

```
java -jar /home/stefan/apps/ili2pg-4.4.5/ili2pg-4.4.5.jar --dbhost localhost --dbport 54321 --dbdatabase edit --dbusr admin --dbpwd admin --nameByTopic --strokeArcs --disableValidation --defaultSrsCode 2056 --createGeomIdx --models SO_AGI_Hoehenkurven_3D_Publikation_20210115 --modeldir "model/;http://models.geo.admin.ch" --dbschema agi_hoehenkurven_2014 --schemaimport
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
CREATE INDEX hoehenkurven_hoehenkurve_kote_idx ON agi_hoehenkurven_2014.hoehenkurven_hoehenkurve USING btree (kote);
```

```
export ORG_GRADLE_PROJECT_dbUriEdit=jdbc:postgresql://localhost:54321/edit
export ORG_GRADLE_PROJECT_dbUserEdit=admin
export ORG_GRADLE_PROJECT_dbPwdEdit=admin
export ORG_GRADLE_PROJECT_dbUriPub=jdbc:postgresql://localhost:54322/pub
export ORG_GRADLE_PROJECT_dbUserPub=admin
export ORG_GRADLE_PROJECT_dbPwdPub=admin
```

```
gradle transfer -Dorg.gradle.jvmargs=-Xmx1G
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

