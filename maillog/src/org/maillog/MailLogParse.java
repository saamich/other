package org.maillog;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Calendar;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;

import static org.elasticsearch.node.NodeBuilder.*;


public class MailLogParse {
	static String index,type="maillog",year;
	static boolean sleepIndexCreate=true;
	
	public static void main(String[] args) {
		String usage="Usage: infile";
		int sleep=1000;
		if (args.length != 1) {
			System.out.println(usage);
			System.exit(1);
		}
		File file=new File(args[0]);
		if (!file.exists()) {
			System.out.println("Files "+args[0]+" not exists");
			System.out.println(usage);
			System.exit(1);
		}
				
		Client client=buildClient();
		CleanScheduler scheduler= new CleanScheduler(client);
		while(sleepIndexCreate){
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		index=scheduler.index;
		year=String.valueOf(scheduler.date.get(Calendar.YEAR));
		BulkRequestBuilder bulkRequest = client.prepareBulk();
		
		try (RandomAccessFile log = new RandomAccessFile(file, "r")) {
			long length = 0;
			int delimeter,queueIdLenght;
			String logStr,json;
			String timestamp,messages,messagesOrigTo, messagesToNoqueue,messagesFromNoqueue;
			String queueId,queueIdNext,messageId,from,to,origTo,statusString,status,fqdn,ip,interimStatus;
			do {
			    if(log.length()<length){ 
			        log.seek(0);
			    }
				logStr = log.readLine();
				if (logStr == null) {
					Thread.sleep(sleep);
					continue;
				}
				logStr = logStr.toLowerCase();
				queueId=null;queueIdNext=null; messageId=null;from=null;to=null;origTo=null;status=null;
				statusString=null;fqdn=null;ip=null;interimStatus=null;json=null;
				// Timestamp всегда в первых 15 символах
				timestamp=logStr.substring(0, 15);
				// Отрезает по первому встреченному ':' после даты
				delimeter=logStr.indexOf(':', 16);
				messages=logStr.substring(delimeter+2);
				//progname=logStr.substring(logStr.indexOf(' ', 17)+1, delimeter);
				queueIdLenght = messages.indexOf(':');
				try {
					// промежуточный статус
					if (queueIdLenght != -1) {
					queueId = messages.substring(0, queueIdLenght);
					if (queueId.equals("noqueue") | messages.contains("reject:")) {
						if (!messages.contains("reject: mail")) {
							messagesFromNoqueue = messages.substring(messages.indexOf(';'));
							from = messagesFromNoqueue.substring(messagesFromNoqueue.indexOf('<')+1,messagesFromNoqueue.indexOf('>'));
							if (from.equals("") ) from="null-sender";
							messagesToNoqueue = messagesFromNoqueue.substring(messagesFromNoqueue.indexOf('>')+1);
							to = messagesToNoqueue.substring(messagesToNoqueue.indexOf('<')+1,messagesToNoqueue.indexOf('>'));
						}
						if (queueId.equals("noqueue")){ 
							// 27 символов "NOQUEUE: reject: MAIL from "
							fqdn=messages.substring(27, messages.indexOf('['));
							if (fqdn.equals("unknown")) fqdn=null;
							ip=messages.substring(messages.indexOf('[')+1,messages.indexOf(']'));
							queueId=null;
						}
						statusString=(messages.substring(messages.indexOf(']')+3, messages.indexOf(';'))).replace("\"", "'");
						status="reject";
					} else {
						if (messages.contains("from=")) {
							from = messages.substring(messages.indexOf('<') + 1,messages.indexOf('>'));
							if (from.equals("") ) from="null-sender"; 
						}
						if (messages.contains("to=")) {
							to = messages.substring(messages.indexOf('<') + 1,messages.indexOf('>'));
						}
						if (messages.contains("orig_to=")) {
							messagesOrigTo=messages.substring(messages.indexOf('>')+1);
							origTo = messagesOrigTo.substring(messagesOrigTo.indexOf('<')+1,messagesOrigTo.indexOf('>'));
						}
						if (messages.contains("message-id=")) {
							messageId = messages.substring(messages.indexOf('=') + 1);
						}
						if (messages.contains("status=")) {
							if (messages.contains("from=")){
								statusString = (messages.substring(messages.lastIndexOf(">") + 10)).replace("\"", "'");
							} else {
								statusString = (messages.substring((messages.substring(0,messages.indexOf('('))).lastIndexOf(",") + 9)).replace("\"", "'");
							}
							if (statusString.contains("127.0.0.1")){
								queueIdNext=statusString.substring(statusString.lastIndexOf(" ")+1, statusString.lastIndexOf(")"));
								interimStatus=statusString.substring(0, statusString.indexOf(" "));
							} else {
								status=statusString.substring(0, statusString.indexOf(" "));
							}
						}
					}
				}
				json="{"+"\"timestamp\":\""+year+" "+timestamp+"\"";
				if (queueId != null) {
					json=json+",\"queueId\":\""+queueId+"\"";
				}
				if (messageId != null) {
					json=json+",\"messageId\":\""+messageId+"\"";
				}
				if (from != null) {
					json=json+",\"from\":\""+from+"\"";
				}
				if (to != null) {
					json=json+",\"to\":\""+to+"\"";
				}
				if (origTo != null) {
					json=json+",\"origTo\":\""+origTo+"\"";
				}
				if (statusString != null) {
					json=json+",\"statusString\":\""+statusString+"\"";
				}
				if (queueIdNext != null) {
					json=json+",\"queueIdNext\":\""+queueIdNext+"\"";
				}
				if (status != null) {
					json=json+",\"status\":\""+status+"\"";
				}
				if (fqdn != null) {
					json=json+",\"fqdn\":\""+fqdn+"\"";
				}
				if (ip != null) {
					json=json+",\"ip\":\""+ip+"\"";
				}
				if (interimStatus != null) {
					json=json+",\"interimStatus\":\""+interimStatus+"\"";
				}
				json=json+"}";
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Bad string:\n"+logStr+"\n");
				}
				try {
					//System.err.println("Json\n"+json+"\n");
					bulkRequest.add(client.prepareIndex(index, type).setSource(json));
					if (bulkRequest.numberOfActions() >= 10) {
						BulkResponse bulkResponse = bulkRequest.execute().actionGet();
						if (bulkResponse.hasFailures()) {
							System.err.println("Error insert bulk");
							for (BulkItemResponse item : bulkResponse.getItems()) {
								if(item.getFailureMessage() != null) {
									System.err.println(item.getItemId()+": "+item.getFailureMessage());
								}
							}
						}
						bulkRequest = client.prepareBulk();
					}
				}catch (Exception e) {
						e.printStackTrace();
						//System.err.println("Bad string:\n"+bulkRequest()+"\n");
				}
				length = log.length();
			} while (true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static Client buildClient(){
		//Node node = nodeBuilder().local(true).node();
		Node node = nodeBuilder().node();
		return node.client();
	}
}