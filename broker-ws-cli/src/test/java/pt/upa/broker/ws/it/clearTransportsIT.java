package pt.upa.broker.ws.it;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class clearTransportsIT extends BaseBrokerIT {
	
	@Test
    public void testClearTransports() throws Exception {
    	
    	client.requestTransport("Lisboa", "Coimbra", 30);
    	client.requestTransport("Lisboa", "Leiria", 20);
    	client.requestTransport("Lisboa", "Aveiro", 40);
		
    	client.clearTransports();
    	
    	assertTrue(client.listTransports().isEmpty());
    	
		
    }
	
	@Test
    public void testClearRequestTransports() throws Exception {
    			
    	client.clearTransports();
    	client.requestTransport("Lisboa", "Coimbra", 30);

    	assertTrue(client.listTransports().size() == 1);
    			
    }
	
	
}
