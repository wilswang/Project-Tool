
package test;

import tool.whiteLabel.WhiteLabelConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public class WhiteLabelConfigTest {

    @Test
    public void testLombokGetter_isSqlOnly() {
        WhiteLabelConfig wl = new WhiteLabelConfig();
        wl.setSqlOnly(true);
        assertTrue(wl.isSqlOnly());
    }

    @Test
    public void testLombokGetter_ticketNo() {
        WhiteLabelConfig wl = new WhiteLabelConfig();
        wl.setTicketNo("T12345");
        assertEquals("T12345", wl.getTicketNo());
    }
}
