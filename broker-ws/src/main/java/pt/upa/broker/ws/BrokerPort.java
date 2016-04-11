package pt.upa.broker.ws;

import javax.jws.WebService;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

import javax.xml.registry.JAXRException;

import pt.upa.broker.ws.TransportStateView;
import pt.upa.broker.ws.TransportView;

import pt.upa.transporter.ws.TransporterPortType;
import pt.upa.transporter.ws.JobView;
import pt.upa.transporter.ws.BadJobFault_Exception;
import pt.upa.transporter.ws.BadLocationFault_Exception;
import pt.upa.transporter.ws.BadPriceFault_Exception;


@WebService(
  endpointInterface="pt.upa.broker.ws.BrokerPortType",
  wsdlLocation="broker.1_0.wsdl",
  name="BrokerWebService",
  portName="BrokerPort",
  targetNamespace="http://ws.broker.upa.pt/",
  serviceName="BrokerService"
)
public class BrokerPort implements BrokerPortType {

  private class BrokerTransportView {
    private String _transporterId;
    public TransportView _transportView;

    public BrokerTransportView(String id, String origin, String dest){
      _transporterId = "";

      TransportView t = new TransportView();
      t.setId(id);
      t.setOrigin(origin);
      t.setDestination(dest);
      t.setTransporterCompany("");
      t.setState(TransportStateView.REQUESTED);

      _transportView = t;
    }

    // Transport view attribute setters
    public void setTransportPrice(int price) { _transportView.setPrice(price); }
    public void setTransportCompany(String company) { _transportView.setTransporterCompany(company); }
    public void setTransportState(TransportStateView state) { _transportView.setState(state); }    

    // Transport view attribute getters
    public String getTransportCompany() { return _transportView.getTransporterCompany(); }
    public TransportStateView getTransportState() { _transportView.getState(); }
    public TransportView getTransportView() { return _transportView; }

    // Id given by transporter company setter and getter
    public void setTransporterId(String id) { _transporterId = id; }
    public String getTransporterId() { return _transporterId; }
  }

  TransporterCompaniesManager _transportersManager;
  List<BrokerTransportView> _transports;

  public BrokerPort() {
    _transportersManager = new TransporterCompaniesManager();
    _transports = new ArrayList<BrokerTransportView>();
  }

  @Override
  public String ping(String name){
    return "Alive: " + name;
  }

  @Override
  public String requestTransport(String origin, String destination, int price) throws 
      UnknownLocationFault_Exception,
      InvalidPriceFault_Exception,
      UnavailableTransportFault_Exception,
      UnavailableTransportPriceFault_Exception {

    // TODO: check if origin and destination are known

    if (price < 0){ 
      InvalidPriceFault faultInfo = new InvalidPriceFault();
      faultInfo.setPrice(price);
      throw new InvalidPriceFault_Exception("Invalid price.", faultInfo);
    }

    Collection<TransporterPortType> temp;
    try {
      temp = _transportersManager.getAllTransporterPorts();

    } catch (JAXRException e) {
      UnavailableTransportFault faultInfo = new UnavailableTransportFault();
      faultInfo.setOrigin(origin); faultInfo.setDestination(destination);
      throw new UnavailableTransportFault_Exception("Error connecting to transporters. PLease try again.", faultInfo);
    }
    List<TransporterPortType> transporters = new ArrayList<TransporterPortType>(temp);
    if (transporters == null){ 
      UnavailableTransportFault faultInfo = new UnavailableTransportFault();
      faultInfo.setOrigin(origin); faultInfo.setDestination(destination);
      throw new UnavailableTransportFault_Exception("No transporter companies available.", faultInfo);
    }

    // saves transporter proposals status
    // 1 = no job for that price; 
    // 0 = no transporter between specified locations
    // -1 = at least one valid proposal
    int reason = 0; 

    int bestPrice = Integer.MAX_VALUE, bestJobIndex = 0;

    int previousNumberTransports = _transports.size();
    JobView[] proposedJobs = new JobView[transporters.size()];

    for (int i=0; i<transporters.size(); ++i){
      int thisIndex = previousNumberTransports+i;
      BrokerTransportView t = new BrokerTransportView(thisIndex+"", origin, destination);
      _transports.add(t);

      TransporterPortType port = transporters.get(i);

      JobView job;
      try {
        job = port.requestJob(origin, destination, price);
      } catch (BadLocationFault_Exception e) {
        _transports.set(thisIndex, null); 
        UnknownLocationFault faultInfo = new UnknownLocationFault();
        throw new UnknownLocationFault_Exception("Unknown origin or destination.", faultInfo);

      } catch (BadPriceFault_Exception e) {
        _transports.set(thisIndex, null); 
        InvalidPriceFault faultInfo = new InvalidPriceFault();
        faultInfo.setPrice(price);
        throw new InvalidPriceFault_Exception("Invalid price.", faultInfo);
      }
      proposedJobs[i] = job;

      if (job == null){
        _transports.set(thisIndex, null); 
        if (reason != -1) reason = 0; 
        continue;
      }
      
      _transports.get(thisIndex).setTransportState(TransportStateView.BUDGETED);
      _transports.get(thisIndex).setTransportCompany(job.getCompanyName());
      _transports.get(thisIndex).setTransportPrice(job.getJobPrice());

      int proposedPrice = job.getJobPrice();

      if (proposedPrice > price){ 
        _transports.get(thisIndex).setTransportState(TransportStateView.FAILED);
        if (reason != -1) reason = 1; 
      }
      else reason = -1;
        
      if (proposedPrice < bestPrice) {
        bestPrice = proposedPrice;
        bestJobIndex = thisIndex;
      }
    }

    if (reason == 0) {
      UnavailableTransportFault faultInfo = new UnavailableTransportFault();
      faultInfo.setOrigin(origin); faultInfo.setDestination(destination);
      throw new UnavailableTransportFault_Exception("No transport available for the requested route.", faultInfo);
    }
    if (reason == 1) {
      UnavailableTransportPriceFault faultInfo = new UnavailableTransportPriceFault();
      faultInfo.setBestPriceFound(bestPrice);
      throw new UnavailableTransportPriceFault_Exception("No transport available for the requested price.", faultInfo); 
    }

    for (int i=0; i<transporters.size(); ++i){
      int thisIndex = previousNumberTransports+i;
      if (proposedJobs[i] == null) continue;

      TransporterPortType port = transporters.get(i);
      boolean isAccepted = thisIndex == bestJobIndex;

      TransportStateView state 
          = (isAccepted ? TransportStateView.BOOKED : TransportStateView.FAILED);
      _transports.get(thisIndex).setTransportState(state);
      
      try {
        port.decideJob(proposedJobs[i].getJobIdentifier(), isAccepted);
      } catch(BadJobFault_Exception e) {
        if (isAccepted) {
          UnavailableTransportPriceFault faultInfo = new UnavailableTransportPriceFault();
          throw new UnavailableTransportPriceFault_Exception("This exception" + 
            "should never happen. If it does it indicates a bug.", faultInfo); 
        }
      }
    }
    return bestJobIndex + "";
  }

  @Override
  public TransportView viewTransport(String id) throws 
      UnknownTransportFault_Exception {

    int index;
    try {
      index = Integer.parseInt(id);
    } catch (NumberFormatException e) {
      UnknownTransportFault faultInfo = new UnknownTransportFault();
      faultInfo.setId(id);
      throw new UnknownTransportFault_Exception("No transports match the given transport identifier.", faultInfo);
    }

    // test for invalid id
    if (index >= _transports.size() || index < 0) {
      UnknownTransportFault faultInfo = new UnknownTransportFault();
      faultInfo.setId(id);
      throw new UnknownTransportFault_Exception("No transports match the given transport identifier.", faultInfo);
    }

    // get transport that matches the given id
    BrokerTransportView transport = _transports.get(index);
    if (transport == null) {
      UnknownTransportFault faultInfo = new UnknownTransportFault();
      faultInfo.setId(id);
      throw new UnknownTransportFault_Exception("No transports match the given transport identifier.", faultInfo);
    }
    
    // update state if transport is not COMPLETED or FAILED
      // gets the port handle for the company doing the transport from the transporters manager object
      // if its null do something
      // asks the company (jobStatus method) for the jobView 
      // updates the transportView of the BrokerTransportView with the returned jobState

    // returns the TransportView attribute of the brokerTransportView object
  }

  @Override
  public List<TransportView> listTransports() {
    return new ArrayList<TransportView>();
  }

  @Override
  public void clearTransports() {

  }
}
