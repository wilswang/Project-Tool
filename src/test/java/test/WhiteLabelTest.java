
package test;

import dto.WhiteLabel;
import org.junit.Test;
import static org.junit.Assert.*;

public class WhiteLabelTest {

    @Test
    public void testLombokGetter_isSqlOnly() {
        WhiteLabel wl = new WhiteLabel();
        wl.setSqlOnly(true);
        assertTrue(wl.isSqlOnly());
    }

    @Test
    public void testLombokGetter_ticketNo() {
        WhiteLabel wl = new WhiteLabel();
        wl.setTicketNo("T12345");
        assertEquals("T12345", wl.getTicketNo());
    }
}
