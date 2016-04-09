package pt.upa.transporter;

import javax.xml.ws.Endpoint;

import pt.ulisboa.tecnico.sdis.ws.uddi.UDDINaming;
import pt.upa.transporter.ws.TransporterPort;

public class TransporterApplication {

	public static void main(String[] args) throws Exception {
		System.out.println(TransporterApplication.class.getSimpleName() + " starting...");

		
		String uddiURL = args[0];
		String name = args[1];
		String url = args[2];	

		Endpoint endpoint = null;
		UDDINaming uddiNaming = null;

		try{
			TransporterPort port = new TransporterPort();
			endpoint = Endpoint.publish(url, port);

			uddiNaming = new UDDINaming(uddiURL);
			uddiNaming.rebind(name, url);


			System.out.println("Awaiting connections");
			System.out.println("Press enter to shutdown");
			System.in.read();
		}
		catch (Exception e) {
			System.out.printf("Caught exception: %s%n", e);
			e.printStackTrace();
		}
		finally {
				try {
					if (endpoint != null) {
						// stop endpoint
						endpoint.stop();
						System.out.printf("Stopped %s%n", url);
					}
				} catch (Exception e) {
					System.out.printf("Caught exception when stopping: %s%n", e);
				}
				try {
					if (uddiNaming != null) {
						// delete from UDDI
						uddiNaming.unbind(name);
						System.out.printf("Deleted '%s' from UDDI%n", name);
					}
				} catch (Exception e) {
					System.out.printf("Caught exception when deleting: %s%n", e);
				}
		}



	}

}
