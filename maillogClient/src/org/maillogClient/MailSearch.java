package org.maillogClient;
import java.util.HashSet;
import java.util.Map;

import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
//import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;


public class MailSearch {
	static String dateBegin=null,dateEnd=null;
	static BoolFilterBuilder filter;
	static Client client;
	static boolean debug=false;
	
	public static void main(String[] args) {
		String usage="Usage:\n--to user@example.org --from user@example2.org --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n"
				+ "--to user@example.org --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n"
				+ "--from user@example2.org --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n"
				+ "--host example.org --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n"
				+ "--host 192.0.2.0 --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n"
				+ "--messageId 20131216205027.3176.qmail@backend1.dmz --dateBegin '2013 Dec 17 00:00:00' --dateEnd '2013 Dec 18 00:00:00'\n\n"
				+ "WARNING!\n"
				+ "--host and --messageId use for search regular expressions!\n"
				+ "--host serach show only reject mail";

		String from=null,to=null,host=null,messageId=null;
		String queueIdNext=null,interimStatus=null,interimStatusString=null,timestampFirst=null,timestampFinal=null;
		String queueId=null,status=null,statusString=null,origTo=null;
	    final int nArgs = args.length;
	    if (nArgs < 2) usage(usage);
		try {
			String thisArg;
			for (int argc = 0; argc < nArgs; argc++) {
				thisArg = (args[argc]).toLowerCase();
				 switch( thisArg ){
		            case "--to": to=args[++argc]; break;
		            case "--from": from=args[++argc];break;
		            case "--dateBegin": dateBegin=args[++argc]; break;
		            case "--dateEnd": dateEnd=args[++argc]; break;
		            case "--datebegin": dateBegin=args[++argc]; break;
		            case "--dateend": dateEnd=args[++argc]; break;
		            case "--host": host=args[++argc]; break;
		            case "--messageId": messageId=args[++argc]; break;
		            case "--debug": debug=true; break;
		            case "--help":  usage(usage); break;
		            default: usage(usage);System.exit(1);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			if (debug) e.printStackTrace();
			usage(usage);
		}
		filter = dateFilter();
	    if (from!=null){
	    	filter.must(FilterBuilders.termFilter("from",from.toLowerCase()));
	    }
	    if (from==null && to!=null){
	    	filter.must(FilterBuilders.orFilter(FilterBuilders.termFilter("to",to.toLowerCase()),FilterBuilders.termFilter("origTo",to.toLowerCase())));
	    }
	    if (host!=null){
	    	if (from!=null | to!=null | messageId!=null) usage(usage);
	    	if (host.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")){
	    		filter.must(FilterBuilders.termFilter("ip",host.toLowerCase()));
	    	} else {
	    		filter.must(FilterBuilders.regexpFilter("fqdn",".*"+host.toLowerCase()+".*"));
	    	}
	    }
	    if (messageId!=null){
	    	if (from!=null | to!=null | host!=null) usage(usage);
	    	filter.must(FilterBuilders.regexpFilter("messageId",".*"+messageId.toLowerCase()+".*"));
	    }
	    	      
		Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "logs").build();
		client = new TransportClient(settings)
		.addTransportAddress(new InetSocketTransportAddress("127.0.0.1", 9300));	
		
		try {
			HashSet<String> oldId = new HashSet<String>();
			Map<String,Object> hitMap;
			boolean stopToSearch;
						
			SearchHits allHits=Search(filter);
			if (allHits.getTotalHits() > 0) {
				for (SearchHit allHit : allHits) {
					stopToSearch=false;
					hitMap = allHit.sourceAsMap();
										
					// --from, --from & --to search
					if (hitMap.containsKey("queueId")){
						queueId=(String)hitMap.get("queueId");
						if (oldId.contains(queueId)) continue;
						timestampFirst=(String) hitMap.get("timestamp");
						filter = dateFilter();
						filter.must(FilterBuilders.termFilter("queueId",queueId));
						Map<String,Object> customHitMap;
						
						// get before Antivirus
						for (SearchHit hit :Search(filter)){
							customHitMap=hit.sourceAsMap();
							if (customHitMap.containsKey("queueIdNext")) {
								queueIdNext=(String) customHitMap.get("queueIdNext");
								oldId.add(queueIdNext);
							}
							if(customHitMap.containsKey("interimStatus")){
								// For --from & --to search
								if (to!=null && from!=null) {
									if (!to.equals((String) customHitMap.get("to"))) {
										stopToSearch=true;
										break;
									}
								}
								if (to==null) to=(String) customHitMap.get("to");
								//
								interimStatus=(String) customHitMap.get("interimStatus");
								interimStatusString=(String) customHitMap.get("statusString");
							}
							if(from==null && customHitMap.containsKey("from")) 
								from=(String)customHitMap.get("from"); 
						}
						if (stopToSearch) continue;
						// get after Antivirus
						if (queueIdNext!=null){
							filter = dateFilter();
							filter.must(FilterBuilders.termFilter("queueId",queueIdNext));
							for (SearchHit hit :Search(filter)){
								customHitMap=hit.sourceAsMap();
								timestampFinal=(String) customHitMap.get("timestamp");
								if(customHitMap.containsKey("status")){
									if(customHitMap.containsKey("origTo")) origTo=(String)customHitMap.get("origTo"); 
									status=(String) customHitMap.get("status");
									statusString=(String) customHitMap.get("statusString");
									break;
								}
							}
						}
						if (debug) {
							System.out.println("queueId: "+queueId);
							System.out.println("queueIdNext: "+queueIdNext);
						}
						System.out.println("\nRecived time: "+timestampFirst);
						System.out.println("From: "+from);
						System.out.println("To: "+to);
						if(origTo!=null) System.out.println("origTo: "+origTo);
						System.out.println("Sent to Antivirus: "+interimStatus);
						if (interimStatus.contains("sent")){
							System.out.println("General status: "+status);
							if (!status.contains("sent")){
								System.out.println("StatusString: "+statusString);
							}
							System.out.println("End processing time: "+timestampFinal);
						} else {
							System.out.println("Antivirus status string: "+interimStatusString);
						}
					} else {
						// NOQUEUE search
						if (hitMap.containsKey("status")) {
							if (to!=null && from!=null) {
								if (!to.equals((String) hitMap.get("to"))) {
									continue;
								}
							} else {
								to=(String)hitMap.get("to");
							}
							if (to==null) to=(String) hitMap.get("to");
							if (from==null) from=(String)hitMap.get("from");
							System.out.println("\nRecived time:" + hitMap.get("timestamp"));
							System.out.println("From: " + from);
							System.out.println("To: " + to);
							System.out.println("General status: " + hitMap.get("status"));
							System.out.println("StatusString: "+hitMap.get("statusString"));
						}

					}
					//hitMap.get("queueId")
					// Show all key
					// hit.sourceAsMap().keySet()
					// if (hit.sourceAsMap().containsKey("queueId"))
					// System.out.println("key exists");
					// if (hit.sourceAsMap().) System.out.println("key exists");
					// System.out.println(hit.sourceAsMap().get("queueId"));
					//System.out.println(response);
				}
			} else {
				System.out.println("\nNo matches found");
			}
	} catch (SearchPhaseExecutionException e) {
		if (debug) e.printStackTrace();
		System.err.println("\nERROR! This is not valid arguments\n");
		usage(usage);
	} catch (NoNodeAvailableException e) {
		if (debug) e.printStackTrace();
		System.err.println("\nERROR! Server not available");
	}
		
	}
	static void usage(String usage){
		System.out.println(usage);
		System.exit(1);
	}
	
	static BoolFilterBuilder dateFilter(){
		filter = FilterBuilders.boolFilter();
		if (dateBegin!=null){
	    	if (dateEnd!=null){
	    		filter.must(FilterBuilders.rangeFilter("timestamp").gte(dateBegin.toLowerCase()).lte(dateEnd.toLowerCase()));
	    	} else {
	    		filter.must(FilterBuilders.rangeFilter("timestamp").gte(dateBegin.toLowerCase()));
	    	}
	    }
	    if (dateBegin==null && dateEnd!=null){
	    		filter.must(FilterBuilders.rangeFilter("timestamp").lte(dateEnd.toLowerCase()));
	    }
	    return filter;
	}
	
	static SearchHits Search(BoolFilterBuilder filter) {
		SearchRequestBuilder searchRequest = client.prepareSearch()
				.setTypes("maillog")
				// .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), filter))
				// .setFilter(filter) // Filter
				.setFrom(0).setSize(1000).setExplain(false)
				.addSort("timestamp", SortOrder.ASC);
		if (debug) System.out.println(searchRequest.toString());
		SearchResponse response = searchRequest.execute().actionGet();
		return response.getHits();
	}
}
