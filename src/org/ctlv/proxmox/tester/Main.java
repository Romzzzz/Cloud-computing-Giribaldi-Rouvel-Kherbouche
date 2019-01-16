package org.ctlv.proxmox.tester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import org.ctlv.proxmox.api.ProxmoxAPI;
import org.ctlv.proxmox.api.data.LXC;
import org.ctlv.proxmox.api.data.Node;
import org.json.JSONException;

//import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil.ToStringAdapter;



public class Main {

	// DATACENTER MEMORY INFORMATIONS
	static long MAX_MEM = (long)(23.53*10740000*16);		// 16% ACCORDING TO THE SUBJECT 
	static long	CRTICAL_MEM = (long)(23.53*10740000*5);		// 12% ACCORDING TO THE SUBJECT 5% FOR TEST PURPOSE
	static long	BALANCE_MEM = (long)(23.53*10740000*3);		// 8%  ACCORDING TO THE SUBJECT 3% FOR TEST PURPOSE
	
	// SEVER NAMES CONSTANTS
	static String srv9 = "srv-px9";
	static String srv10 = "srv-px10";
	
	public static void main(String[] args) throws LoginException, JSONException, IOException, InterruptedException {
		// MAIN PROGRAM FUNCTION
		// API INSTANCIATION
		ProxmoxAPI api = new ProxmoxAPI();
		
		// LOCAL VARIABLES
		int CT_number = 0;				// CT COUNT
		long TAB_CT[] = new long[101];	// CT TABLE
		Boolean generation = true;		// ALLOWED CT CREATION
		int status9 = 0;				// STATE SERVER 9
		int status10 = 0;				// STATE SERVER 10
		
		// Connect to database
		api.login();
		
		Clean_server(srv9, api);
		Clean_server(srv10, api);
		//CLEAR OUR RESILIENT CTs
		
		// CONTROL LOOP
		while (CT_number < 30) {		// THE CONTROL LOOP IS BASED ON THE EMPIRIC NUMBER OF CT CREATED
			// TEST IF CT CREATION IS ALLOWED
			if (generation) {
				// CREATE A CT AND STORE IT'S ID IN THE TABLE
				TAB_CT[CT_number] = Launch_CT(CT_number, api, Server_flip(0.95)); //HERE IS THE SET UP FOR THE SERVER ATTRIBUTION PROBABILITY
						// USES THE NUMBER OF CURRENT CT, THE API AND A RANDOM GENERATOR FOR THE SERVER CHOICE
				CT_number++; 		// INCREMENTS CT COUNT ONY IF ONE IS GENERATED
			}
			// GETTING SERVER INFORMATION
			status9 = Info_srv(srv9, api, TAB_CT);		// ONLY ABOUT OUR CTs
			status10 = Info_srv(srv10, api, TAB_CT);	// ONLY ABOUT OUR CTs
			
			if(status9 == 1) { 											// IF SERVER 9 FULL (16%)
				generation = false;										// STOP CT CREATION
			} else {
				if (status10 == 1) { 									// IF SERVER 10 FULL (16%)
					generation = false;									// STOP CT CREATION
				} else {
					if(status9 == 2) {									// IF SERVER 9 IS ALMOST FULL (12%)
						if(Quiet_CT(srv9, api, TAB_CT) == 0) {			// SHUTDOWN OLDEST CT
							generation = false;							// STOP CT CREATION IN CASE OF FAILURE
						}
					}
					if(status10 == 2) {									// IF SERVER 10 IS ALMOST FULL (12%)
						if(Quiet_CT(srv10, api, TAB_CT) == 0) {			// SHUTDOWN OLDEST CT
							generation = false;							// STOP CT CREATION IN CASE OF FAILURE
						}
					}
					if(status9 == 3 && status10 == 0) {					// IF SERVER 9 IS OVERWELHMED AND SERVER 10 HAS FREE SPACE
						if(Migrate(srv9, srv10, api, TAB_CT) == 0) {	// MIGRATE OLDEST CT FROM SERVER 9 TO SERVER 10
							generation = false;							// STOP CT CREATION IN CASE OF DAILURE
						}
					}
					if(status9 == 0 && status10 == 3) {					// IF SERVER 10 IS OVERWHELMED AND SERVER 9 HAS FREE SPACE
						if(Migrate(srv10, srv9, api, TAB_CT) == 0) {	// MIGRATE OLDEST CT FROM SERVER 10 TO SERVER 9
							generation = false;							// STOP CT CREATION IN CASE OF FAILURE
						}
					}
				}
			}
		TimeUnit.SECONDS.sleep(5); // SLEEP TIME FOR THE PROGRAM LOOP
		}
		// END OF PROGRAM
		System.out.println("***************************************");
		System.out.println("***************************************");
		System.out.println("*****      FIN DU PROGRAMME    ********");
		System.out.println("***************************************");
		System.out.println("***************************************");
		//Clear_CT(srv9, api, TAB_CT);				// CLEAR ALL OUR CT IN SERVER 9
		//Clear_CT(srv10, api, TAB_CT);				// CLEAR ALL OUR CT IN SERVER 10
		Clean_server(srv9, api);
		Clean_server(srv10, api);
		
		
	}
	
	public static int Launch_CT(int number, ProxmoxAPI api, String Srv_name) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION FOR THE CREATION AND THE STARTING OF CTs
		int res = -1;
		String CT_name_base = "ct-tpgei-virt-A2D3-ct";
		int CT_ID_base = 4300;
		String CT_name = CT_name_base + number;
		int CT_ID = CT_ID_base + number;
		System.out.println(CT_name);
		api.createCT(Srv_name, String.valueOf(CT_ID), CT_name, 512);		// CREATE A CT
						// ON THE ATTRIBUTED SERVER WITH THE INCREMENTED ID AND WITH THE GOOD MATCHING NAME
		TimeUnit.SECONDS.sleep(35);
		api.startCT(Srv_name, String.valueOf(CT_ID));						// START THE CT JUST CREATED
		System.out.println("**************************************************************");
		System.out.println("***CREATION****  CT " +  String.valueOf(CT_ID) + "CREATED ON SERVER " + Srv_name+" ***CREATION****");
		System.out.println("*******            CREATED AND STARTED          ***************");
		System.out.println("**************************************************************");		
			// RETURN THE ID OF THE CT JUST CREATED
		res = CT_ID;
		return res;
	}
	
	public static int Info_srv(String srv, ProxmoxAPI api, long tab_ct[]) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION FOR INFORMATION GATHERING ABOUT A SERVER SPECIFIED IN THE PARAMETERS
		int res = -1;
				// GETTING INFORMATION ON THE SPECIFIED NODE
		Node buffer_node = api.getNode(srv);
		System.out.println("Information on Node" + srv);
		System.out.println("Memory usage " + buffer_node.getMemory_used());
		System.out.println("Uptime " + buffer_node.getUptime());
				// GATHERING INFORMATION OF ALL OUR CTs
		//LXC buffer_CT;
		long Total_RAM_usage = 0;
		List<String> List_CT = new ArrayList<String>();
		List_CT.addAll(api.getCTList(srv));					// COMPLETE CT LIST ACQUIRING
		LXC Info_CT;
		String buffer_ID;
		for (int  i = 0; i < tab_ct.length; i++){			
			buffer_ID = String.valueOf(tab_ct[i]);			
			if (List_CT.contains(buffer_ID)) {				// SORTING CT LIST TO ONLY GATHER OURS
				Info_CT = api.getCT(srv, buffer_ID);		// EXTRACTING THEIR MEMORY USAGE
				System.out.println();
				System.out.println("CT ID : " + String.valueOf(tab_ct[i]));
				System.out.println("CT Information");
				System.out.println("Memory Usage " + Info_CT.getMem());
				Total_RAM_usage += Info_CT.getMem();		// SUM OF ALL OUR MEMORY USAGE INSIDE A DATACENTER
			}
		}
		System.out.println();
		System.out.println(Total_RAM_usage*100/(23.53*1074000000));
		System.out.println();
		if (Total_RAM_usage > MAX_MEM) {					// TEST AGAINST MAXIMUM MEMORY USAGE ALLOWED VALUE (16%)
			res = 1;										// RETURN 1 IF TRUE
			System.out.println("**************************************************************");
			System.out.println("***CRITICAL****     Current Mémory usage on" + srv + " : " + Total_RAM_usage + "   ***CRITICAL****");
			System.out.println("*******            NEED TO ABORT CTs           ***************");
			System.out.println("**************************************************************");
		} else {
			if (Total_RAM_usage > CRTICAL_MEM) {			// TEST AGAINST CRITICAL MEMORY USAGE VALUE (12%)
				res = 2;									// RETURN 2 IF TRUE
				System.out.println("**************************************************************");
				System.out.println("***DANGER****     Current Mémory usage on" + srv + " : " + Total_RAM_usage + "   ***DANGER****");
				System.out.println("*********            NEED TO STOP CTs           **************");
				System.out.println("**************************************************************");
			} else {
				if (Total_RAM_usage > BALANCE_MEM) {		// TEST AGAINST AVERAGE MEMORY USAGE VALUE (8%)
					res = 3;								// RETURN 3 IF TRUE
					System.out.println("****************************************************************");
					System.out.println("***EQUILIBRIUM****     Current Mémory usage on" + srv + " : " + Total_RAM_usage + "   *****EQUILIBRIUM****");
					System.out.println("*********            NEED TO MIGRATE CTs           **************");
					System.out.println("****************************************************************");
				} else {
					res = 0;								// RETURN 0 IF EVERY OTHER ONE IS FALSE
				}
			}
		}
		return res;		// RETURN RESULT AT THE END
	}
	
	public static int Migrate (String srv_src, String srv_dst, ProxmoxAPI api, long tab_ct[]) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION FOR CT MIGRATION FROM ONE SERVER TO THE OTHER AS SPECIFIED IN PARAMETERS
		int res = -1;
		int i = 0;
		List<String> List_CT = new ArrayList<String>();
		List_CT.addAll(api.getCTList(srv_src));				
		while(!List_CT.contains(String.valueOf(tab_ct[i]))) {				// FINDING OUR OLDEST CT IN THE SOURCE SERVER
			i++;
		}
		api.stopCT(srv_src, String.valueOf(tab_ct[i]));					// STOPPING THE CT FOR MIGRATION
		TimeUnit.SECONDS.sleep(3);	
		api.migrateCT(srv_src, String.valueOf(tab_ct[i]), srv_dst);		// MIGRATING OUR OLDEST CT FROM SOURCE SERVER 
		TimeUnit.SECONDS.sleep(10);										//			TO DESTINATION SERVER
		api.startCT(srv_dst, String.valueOf(tab_ct[i]));				// START THE JUST MIGRATED CT IN THE NEW SERVER
		TimeUnit.SECONDS.sleep(3);
		res = 1;														// RETURN 1 IF EVERY THING GOOD
		System.out.println("****************************************************************");
		System.out.println("*** MIGRATION  CT ID : " + String.valueOf(tab_ct[i]) + "           *****MIGRATION****");
		System.out.println("*** MIGRATION  " + srv_src + " vers " + srv_dst + " *****MIGRATION****");
		System.out.println("***************       DONE and RUNNING           ****************");
		System.out.println("****************************************************************");
		return res;
	}
	
	public static int Quiet_CT (String srv, ProxmoxAPI api, long tab_ct[]) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION TO SHUTDOWN CTs IN CASE OF SERVER OVERLOAD
		int res = -1;
		int i = 0;
		List<String> List_CT = new ArrayList<String>();
		List_CT.addAll(api.getCTList(srv));
		while(!List_CT.contains(String.valueOf(tab_ct[i]))) {				// FINDING OUR OLDEST CT IN THE SPECIFIED SERVER
			i++;
		}
		api.stopCT(srv, String.valueOf(tab_ct[i]));			// STOPPING CTs FOR QUIET
		TimeUnit.SECONDS.sleep(3);	
		res = 1;											// RETURN 1 IF EVERY THING GOOD
		System.out.println("****************************************************************");
		System.out.println("*** QUIET  CT ID : " + String.valueOf(tab_ct[i]) + "     *****QUIET****");
		System.out.println("*** QUIET  on serveur : " + srv +  "       *****QUIET****");
		System.out.println("***************       STOP           ****************");
		System.out.println("****************************************************************");
		// NO WAKING UP HANDLER IN THE SUBJECT, THE STOPPED CT HERE WILL NEVER BE AWAKEN
		// IF NEEDED COMMENT/UNCOMMENT THE 2 FOLLOWING LINES TO MODIFY THAT
		api.deleteCT(srv, String.valueOf(tab_ct[i]));
		TimeUnit.SECONDS.sleep(3);
		return res;
	}
	
	public static int Clear_CT (String srv, ProxmoxAPI api, long tab_ct[]) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION TO DELETE ALL CT CREATED
		// NOT USED ANYMORE
		// DOESN'T TAKE IN ACOUNT ALREADY DELETED CTs AND MIGHT NOT WORK
		int res = -1;
		List<String> List_CT = new ArrayList<String>();
		List_CT.addAll(api.getCTList(srv));
		for (int  i = 0; i < tab_ct.length; i++){				// FINDING OUR OLDEST CT IN THE SPECIFIED SERVER
			if (List_CT.contains(String.valueOf(tab_ct[i]))) {
				api.stopCT(srv, String.valueOf(tab_ct[i]));		// STOPPING CTs FOR DESTRUCTION
				TimeUnit.SECONDS.sleep(3);
				api.deleteCT(srv, String.valueOf(tab_ct[i]));	// DELETING OUR CTs FROM SERVER
				TimeUnit.SECONDS.sleep(3);
			}
		}
		res = 1;												// RETURN 1 IF EVERYTHING IS GOOD
		System.out.println("****************************************************************");
		System.out.println("******* CLEAR ****  ON SERVER : " + srv + "     *****CLEAR****");
		System.out.println("***************       DELETED           ****************");
		System.out.println("****************************************************************");
		return res;
	}
	
	public static int Clean_server (String srv, ProxmoxAPI api) throws InterruptedException, LoginException, JSONException, IOException {
		// FUNCTION TO CLEAN A SERVER FROM ALL OF OUR CTs
		int res = -1;
		List<String> List_CT = new ArrayList<String>();			// GET ALL CTs IN THE SPECIFIED SERVER
		List_CT.addAll(api.getCTList(srv));						
		for(int i = 0; i<List_CT.size(); i++) {					// GO THROUGH ALL CTs IN THE SERVER
			if(List_CT.get(i).getBytes()[0] ==  52 && List_CT.get(i).getBytes()[1] == 51)	// SELECT ONLY OURS
			{
				api.stopCT(srv, List_CT.get(i));				// STOP OUR CT
				TimeUnit.SECONDS.sleep(3);
				api.deleteCT(srv, List_CT.get(i));				// DELETE OUR CT
				TimeUnit.SECONDS.sleep(3);
				// THIS FUNCTION DOESN'T DELETE CT THAT AREN'T RUNNING, WE DON'T NEED IT TO
				// IF DESIRE, ADD A CATCH/THROW EXPRESSION ON THE STOP_CT FUNCTION
			}
		}
		return res;
	}
	
	public static String Server_flip (double proba) {
		// FUNCTION TO GENERATE A RANDOM PROBABILITY FOR SERVER ATTRIBUTION
		double u = Math.random();			// RANDOM GENERATOR BETWEEN 0/1
		if(u < proba) {						// COMPARE IT AGAINST SPECIFIED VALUE
			return "srv-px9";				// RETURN THE CORRECT STRING ACCORDING TO RESULT
		} else {
			return "srv-px10";
		}
	}

}
