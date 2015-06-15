package net.openhft.chronicle.engine;

import net.openhft.chronicle.engine.api.tree.RequestContext;
import org.junit.Test;

import static net.openhft.chronicle.engine.api.tree.RequestContext.requestContext;
import static org.junit.Assert.assertEquals;

/**
 * Created by daniel on 29/05/15.
 */
public class RequestContextTest {

    @Test
    public void testParsing() {
        String uri = "/chronicleMapString?" +
                "view=map&" +
                "keyType=java.lang.String&" +
                "valueType=string&" +
                "putReturnsNull=true&" +
                "removeReturnsNull=false&" +
                "bootstrap=true";
//        System.out.println(uri);
        RequestContext rc = requestContext(uri);
        assertEquals("RequestContext{pathName='',\n" +
                "name='chronicleMapString',\n" +
                "viewType=interface net.openhft.chronicle.engine.api.map.MapView,\n" +
                "type=class java.lang.String,\n" +
                "type2=class java.lang.String,\n" +
                "basePath='null',\n" +
                "wireType=TEXT,\n" +
                "putReturnsNull=true,\n" +
                "removeReturnsNull=false,\n" +
                "bootstrap=true,\n" +
                "averageValueSize=0.0,\n" +
                "entries=0,\n" +
                "tcpBufferSize=1024,\n" +
                "port=0,\n" +
                "host=,\n" +
                "timeout=1000,\n" +
                "recurse=null}", rc.toString().replaceAll(", ", ",\n"));
        assertEquals(Boolean.TRUE, rc.putReturnsNull());
        assertEquals(Boolean.FALSE, rc.removeReturnsNull());
        assertEquals(Boolean.TRUE, rc.bootstrap());
    }

    @Test
    public void parseDirectory(){
        String uri = "/grandparent/parent/child/";
        RequestContext rc = requestContext(uri);
        assertEquals("child", rc.name());
    }

    @Test
    public void parseEmptyString(){
        String uri = "";
        RequestContext rc = requestContext(uri);
        assertEquals("", rc.name());
    }
}