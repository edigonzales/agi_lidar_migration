package agi.lidar

import ch.interlis.iom_j.itf.impl.jtsext.geom.ArcSegment
import ch.interlis.iom_j.itf.impl.jtsext.geom.CompoundCurve
import ch.interlis.iom_j.itf.impl.jtsext.geom.CompoundCurveRing
import ch.interlis.iom_j.itf.impl.jtsext.geom.CurveSegment
import ch.interlis.iom_j.itf.impl.jtsext.geom.StraightSegment

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.interlis2.av2geobau.impl.DxfUtil;
import org.interlis2.av2geobau.impl.DxfWriter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.jts.Jts2iox;
import ch.interlis.iox_j.jts.Iox2jtsext;

import ch.interlis.ioxwkf.gpkg.GeoPackageReader;

class Gpkg2Dxf {
    private static final String POLYLINE = "POLYLINE";
    public static final String IOM_ATTR_GEOM = "geom";
    public static final String IOM_ATTR_LAYERNAME = "layername";
    private static int precision = 3;

    public void execute(String gpkgFile, String dxfFile) throws Exception {
        // Get all geopackage tables that will be converted to dxf files.
        String sql = "SELECT \n" +
                "    table_prop.tablename, \n" +
                "    gpkg_geometry_columns.column_name,\n" +
                "    gpkg_geometry_columns.srs_id AS crs,\n" +
                "    gpkg_geometry_columns.geometry_type_name AS geometry_type_name,\n" +
                "    classname.IliName AS classname,\n" +
                "    attrname.SqlName AS dxf_layer_attr\n" +
                "FROM \n" +
                "    T_ILI2DB_TABLE_PROP AS table_prop\n" +
                "    LEFT JOIN gpkg_geometry_columns\n" +
                "    ON table_prop.tablename = gpkg_geometry_columns.table_name\n" +
                "    LEFT JOIN T_ILI2DB_CLASSNAME AS classname\n" +
                "    ON table_prop.tablename = classname.SqlName \n" +
                "    LEFT JOIN ( SELECT ilielement, attr_name, attr_value FROM T_ILI2DB_META_ATTRS WHERE attr_name = 'dxflayer' ) AS meta_attrs \n" +
                "    ON instr(meta_attrs.ilielement, classname) > 0\n" +
                "    LEFT JOIN T_ILI2DB_ATTRNAME AS attrname \n" +
                "    ON meta_attrs.ilielement = attrname.IliName \n" +
                "WHERE\n" +
                "    setting = 'CLASS'\n" +
                "    AND \n" +
                "    column_name IS NOT NULL";

        List<DxfLayerInfo> dxfLayers = new ArrayList<DxfLayerInfo>();
        String url = "jdbc:sqlite:" + gpkgFile;
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while(rs.next()) {
                    DxfLayerInfo dxfLayerInfo = new DxfLayerInfo();
                    dxfLayerInfo.setTableName(rs.getString("tablename"));
                    dxfLayerInfo.setGeomColumnName(rs.getString("column_name"));
                    dxfLayerInfo.setCrs(rs.getInt("crs"));
                    dxfLayerInfo.setGeometryTypeName(rs.getString("geometry_type_name"));
                    dxfLayerInfo.setClassName(rs.getString("classname"));
                    dxfLayerInfo.setDxfLayerAttr(rs.getString("dxf_layer_attr"));
                    dxfLayers.add(dxfLayerInfo);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new IllegalArgumentException(e.getMessage());
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        GeometryFactory geometryFactory = new GeometryFactory();

        // TODO Es darf nur eine Tabelle geben. Sonst wird die Datei immer überschrieben.
        for (DxfLayerInfo dxfLayerInfo : dxfLayers) {
            String tableName = dxfLayerInfo.getTableName();
            String geomColumnName = dxfLayerInfo.getGeomColumnName();
            int crs = dxfLayerInfo.getCrs();
            String geometryTypeName = dxfLayerInfo.getGeometryTypeName();
            String dxfLayerAttr = dxfLayerInfo.getDxfLayerAttr();

            String dxfFileName = Paths.get(dxfFile).toFile().getAbsolutePath();
            java.io.Writer fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dxfFileName, false), "ISO-8859-1"));

            try {
                writeBlocks(fw);
                fw.write(DxfUtil.toString(0, "SECTION"));
                fw.write(DxfUtil.toString(2, "ENTITIES"));

                GeoPackageReader reader = new GeoPackageReader(new File(gpkgFile), tableName);
                IoxEvent event = reader.read();
                while (event instanceof IoxEvent) {
                    if (event instanceof ObjectEvent) {
                        ObjectEvent iomObjEvent = (ObjectEvent) event;
                        IomObject iomObj = iomObjEvent.getIomObject();

                        String layer;
                        if (dxfLayerAttr != null) {
                            layer = iomObj.getattrvalue(dxfLayerAttr);
                            layer = layer.replaceAll("\\s+","");
                        } else {
                            layer = "default";
                        }
                        IomObject iomGeom = iomObj.getattrobj(geomColumnName, 0);

                        Geometry jtsGeom;
                        if (iomGeom.getobjecttag().equals(POLYLINE)) {
                            CoordinateList coordList = Iox2jts.polyline2JTS(iomGeom, false, 0);
                            Coordinate[] coordArray = new Coordinate[coordList.size()];
                            coordArray = (Coordinate[]) coordList.toArray(coordArray);
                            jtsGeom = geometryFactory.createLineString(coordArray);

                            IomObject dxfObj = new Iom_jObject(DxfWriter.IOM_2D_POLYLINE, null);
                            dxfObj.setobjectoid(iomObj.getobjectoid());
                            dxfObj.setattrvalue(DxfWriter.IOM_ATTR_LAYERNAME, layer);

                            IomObject polyline = Jts2iox.JTS2polyline((LineString)jtsGeom);
                            dxfObj.addattrobj(DxfWriter.IOM_ATTR_GEOM, polyline);

                            String dxfFragment = feature2Dxf(dxfObj);
                            fw.write(dxfFragment);
                        } else {
                            continue;
                        }
                    }
                    event = reader.read();
                }

                if (reader != null) {
                    reader.close();
                    reader = null;
                }

                fw.write(DxfUtil.toString(0, "ENDSEC"));
                fw.write(DxfUtil.toString(0, "EOF"));
            } finally{
                if(fw != null) {
                    fw.close();
                    fw=null;
                }
            }
        }
    }

    private String feature2Dxf(IomObject feature) {
        String layerName=feature.getattrvalue(IOM_ATTR_LAYERNAME);
        CompoundCurve curve = Iox2jtsext.polyline2JTS(feature.getattrobj(IOM_ATTR_GEOM, 0),false,0.0);
        StringBuffer sb = new StringBuffer();

        // Weil DxfWriter in av2geobau nur 2D Polylinien schreiben kann.
        writePolyline(sb, layerName, curve,true,false);
        return sb.toString();
    }

    private static void writePolyline(StringBuffer sb, String layerName, LineString line,boolean is3D,boolean isClosed) {
        ArrayList<CurveSegment> segs=new ArrayList<CurveSegment>();
        if(line instanceof CompoundCurveRing) {
            CompoundCurveRing ring=(CompoundCurveRing)line;
            ArrayList<CompoundCurve> lines=ring.getLines();
            for(CompoundCurve cline:lines) {
                segs.addAll(cline.getSegments());
            }
        }else if(line instanceof CompoundCurve) {
            segs=((CompoundCurve) line).getSegments();
        }else {
            Coordinate coords = line.getCoordinates();
            for(int coordi=1;coordi<coords.length;coordi++){
                segs.add(new StraightSegment(coords[coordi-1],coords[coordi]));
            }
        }
        sb.append(DxfUtil.toString(0, "POLYLINE"));
        sb.append(DxfUtil.toString(8, layerName));

        sb.append(DxfUtil.toString(66, 1));
        sb.append(DxfUtil.toString(10, "0.0"));
        sb.append(DxfUtil.toString(20, "0.0"));
        sb.append(DxfUtil.toString(30, "0.0"));
        sb.append(DxfUtil.toString(70, (is3D?8:0)+(isClosed?1:0))); // Polyline flag: 1 = closed polyline, 8 = 3D Polyline

        for (int i = 0 ; i < segs.size() ; i++) {
            sb.append(DxfUtil.toString(0, "VERTEX"));
            sb.append(DxfUtil.toString(8, layerName));
            final CurveSegment curveSegment = segs.get(i);
            final Coordinate coord = curveSegment.getStartPoint();
            sb.append(DxfUtil.toString(10, coord.x, precision));
            sb.append(DxfUtil.toString(20, coord.y, precision));
            if (is3D && !Double.isNaN(coord.z)) {
                sb.append(DxfUtil.toString(30, coord.z, precision));
            }else {
                sb.append(DxfUtil.toString(30, 0.0,precision));
            }
            if(curveSegment instanceof ArcSegment) {
                // Bulge (optional; default is 0).
                final ArcSegment arc = (ArcSegment)curveSegment;
                if(!arc.isStraight()) {
                    double bulge=calcBulge(arc);
                    final String bulgeTxt = DxfUtil.toString(42, bulge, precision);
                    sb.append(bulgeTxt);
                }
            }
            sb.append(DxfUtil.toString(70, 1)); // Vertex flag:  1 = Extra vertex created by curve-fitting
        }
        {
            sb.append(DxfUtil.toString(0, "VERTEX"));
            sb.append(DxfUtil.toString(8, layerName));
            final Coordinate coord = segs.get(segs.size()-1).getEndPoint();
            sb.append(DxfUtil.toString(10, coord.x, precision));
            sb.append(DxfUtil.toString(20, coord.y, precision));
            if (is3D && !Double.isNaN(coord.z)) {
                sb.append(DxfUtil.toString(30, coord.z, precision));
            }else {
                sb.append(DxfUtil.toString(30, 0.0,precision));
            }
            sb.append(DxfUtil.toString(70, 1)); // Vertex flag:  1 = Extra vertex created by curve-fitting

        }
        sb.append(DxfUtil.toString(0, "SEQEND"));
    }

    private void writeBlocks(java.io.Writer fw) throws IOException {
        // BLOCK (Symbole)
        fw.write(DxfUtil.toString(0, "SECTION"));
        fw.write(DxfUtil.toString(2, "BLOCKS"));

        // GP Bolzen
        fw.write(DxfUtil.toString(0, "BLOCK"));
        fw.write(DxfUtil.toString(8, "0"));
        fw.write(DxfUtil.toString(70, "0"));
        fw.write(DxfUtil.toString(10, "0.0"));
        fw.write(DxfUtil.toString(20, "0.0"));
        fw.write(DxfUtil.toString(30, "0.0"));
        fw.write(DxfUtil.toString(2, "GPBOL"));
        fw.write(DxfUtil.toString(0, "CIRCLE"));
        fw.write(DxfUtil.toString(8, "0"));
        fw.write(DxfUtil.toString(10, "0.0"));
        fw.write(DxfUtil.toString(20, "0.0"));
        fw.write(DxfUtil.toString(30, "0.0"));
        fw.write(DxfUtil.toString(40, "0.5"));
        fw.write(DxfUtil.toString(0, "ENDBLK"));
        fw.write(DxfUtil.toString(8, "0"));

        fw.write(DxfUtil.toString(0, "ENDSEC"));
    }

    public class DxfLayerInfo {
        private String tableName;
        private String geomColumnName;
        private int crs;
        private String geometryTypeName;
        private String className;
        private String dxfLayerAttr;

        public String getTableName() {
            return tableName;
        }
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        public String getGeomColumnName() {
            return geomColumnName;
        }
        public void setGeomColumnName(String geomColumnName) {
            this.geomColumnName = geomColumnName;
        }
        public int getCrs() {
            return crs;
        }
        public void setCrs(int crs) {
            this.crs = crs;
        }
        public String getGeometryTypeName() {
            return geometryTypeName;
        }
        public void setGeometryTypeName(String geometryTypeName) {
            this.geometryTypeName = geometryTypeName;
        }
        public String getClassName() {
            return className;
        }
        public void setClassName(String className) {
            this.className = className;
        }
        public String getDxfLayerAttr() {
            return dxfLayerAttr;
        }
        public void setDxfLayerAttr(String dxfLayerAttr) {
            this.dxfLayerAttr = dxfLayerAttr;
        }
    }
}
