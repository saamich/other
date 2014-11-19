package org.maillog;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;


public class CleanScheduler implements Runnable{
	Client client;
	String index,type;
	Calendar date;
	public CleanScheduler(Client client){
		this.date=Calendar.getInstance();
		this.client=client;
		this.index=currentIndex();
		this.type=MailLogParse.type;
		Thread CleanSchedulerThread = new Thread(this, "CleanSchedulerThread");
		CleanSchedulerThread.start();
	}

	@Override
	public void run() {
		boolean hours=false;
		String indexOld,indexPrevious;
		if(!indexExists(index)) createIndex();
		MailLogParse.sleepIndexCreate=false;
		try {
			while (true) {
				date=Calendar.getInstance();
				if (index.equals(currentIndex())){
					if (!hours && date.get(Calendar.MINUTE) !=0 ) {
						TimeUnit.MINUTES.sleep(1);
					}else{
						hours=true;
						TimeUnit.HOURS.sleep(1);
					}
				}else{
					indexPrevious=index;
					index=currentIndex();
					if (createIndex()) { 
						MailLogParse.index=index;
						MailLogParse.year=String.valueOf(date.get(Calendar.YEAR));
					}
					indexOld=String.valueOf(date.get(Calendar.YEAR))+String.valueOf(date.get(Calendar.MONTH)+1)+String.valueOf(date.get(Calendar.DAY_OF_MONTH)-7);
					if (indexExists(indexOld)) {
						DeleteIndexResponse response = client.admin().indices().delete(new DeleteIndexRequest(indexOld)).actionGet();
						if (!response.isAcknowledged()){
							System.err.println("Error delete index "+index);
						}
					}
					OptimizeResponse response =	client.admin().indices().prepareOptimize(indexPrevious)
							.setFlush(true).setOnlyExpungeDeletes(true)
							.setWaitForMerge(true).execute().actionGet();
					if (response.getSuccessfulShards() != response.getTotalShards()){
						System.err.println("Error optimize index "+indexPrevious);
					}
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String currentIndex(){
		int year=date.get(Calendar.YEAR);
		int month=date.get(Calendar.MONTH)+1;
		int day=date.get(Calendar.DAY_OF_MONTH);
		return (year+(month < 10 ? "0" : "")+month+(day < 10 ? "0" : "")+day); 
	}
	
	private boolean indexExists(String index){
		return client.admin().indices().prepareExists(index).execute().actionGet().isExists();
	}

	private boolean createIndex() {
		CreateIndexRequest request;
		request = Requests.createIndexRequest(index).settings(settings()).mapping(type, mapping());
		CreateIndexResponse response = client.admin().indices().create(request).actionGet();
		
		if (response.isAcknowledged()) {
			return true;
		} else {
			System.err.println("Eror create index "+index);
			return false;
		}
		
	}
	private String settings(){
		return "{" +
			"\"index\":{"+
			        "\"number_of_shards\":1,"+
			        "\"number_of_replicas\":0"+
			"}}";
	}
	private String mapping(){
		return "{" + 
				"\""+type+"\":{"+
				 "\"_source\":{\"enabled\":true},"+
				 "\"_all\":{\"enabled\":false},"+
				 "\"properties\":{"+
				 	"\"queueId\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"queueIdNext\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"messageId\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"from\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"to\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"origTo\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"statusString\":{\"type\":\"string\",\"index\":\"no\",\"store\":\"no\"},"+
				 	"\"status\":{\"type\":\"string\",\"index\":\"no\",\"store\":\"no\"},"+
				 	"\"fqdn\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"ip\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":\"no\"},"+
				 	"\"interimStatus\":{\"type\":\"string\",\"index\":\"no\",\"store\":\"no\"},"+
				 	"\"timestamp\":{\"type\":\"date\",\"store\":\"no\",\"format\":\"YYYY MMM dd HH:mm:ss||YYYY MMM  d HH:mm:ss\"}"+
				"}}}";
	}
}
