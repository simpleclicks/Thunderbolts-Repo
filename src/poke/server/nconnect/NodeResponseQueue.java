package poke.server.nconnect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eye.Comm.Document;
import eye.Comm.Response;

public class NodeResponseQueue {
	
	static private volatile ConcurrentHashMap<String, NodeClient> activeNodeMap = new ConcurrentHashMap<String, NodeClient>();
	
	//static private volatile ConcurrentHashMap<String, Response> docQueryResponseQueue =  new ConcurrentHashMap<String, Response>();
	
	protected static Logger logger = LoggerFactory.getLogger("NodeResponseQueue");
	
	public static void addActiveNode(String nodeId , NodeClient node){
		
		activeNodeMap.put(nodeId, node);
	}
	
	public static void removeInactiveNode(String nodeId){
		
		activeNodeMap.remove(nodeId);
	}
	
//	public static void addDocQueryReponse(String docNameSpace , Response docQueryResponse){
//		
//		docQueryResponseQueue.put(docNameSpace, docQueryResponse);
//	}
//	
//	public static boolean docQueryCheck(String docNameSpace){
//		
//		return docQueryResponseQueue.contains(docNameSpace);
//	}
	
	public static boolean nodeExistCheck(String nodeId){
		
		return activeNodeMap.containsKey(nodeId);
	}
	
	public static int activeNodesCount(){
		
		return activeNodeMap.size();
	}
	
	public static NodeClient[] getActiveNodeInterface(){
		
		Collection<NodeClient> activeNodes = activeNodeMap.values();

		NodeClient[] activeNodeArray = new NodeClient[activeNodes.size()];

		activeNodes.toArray(activeNodeArray);
		
		return activeNodeArray;
		
	}
	
	public static void broadcastDocQuery(String nameSpace , String fileName){
		
		NodeClient[] activeNodeArray = getActiveNodeInterface();

		for(NodeClient nc: activeNodeArray){

			nc.queryFile(nameSpace, fileName);
		}
	}
	
public static void broadcastNamespaceQuery(String nameSpace){
		
		NodeClient[] activeNodeArray = getActiveNodeInterface();

		for(NodeClient nc: activeNodeArray){

			nc.queryNamespace(nameSpace);
		}
	}
	
public static void broadcastNamespaceListQuery(String nameSpace) {
	// TODO Auto-generated method stub
	
	NodeClient[] activeNodeArray = getActiveNodeInterface();

	for(NodeClient nc: activeNodeArray){

		nc.queryNamespaceList(nameSpace);
	}
	
}
	

	public static boolean fetchDocQueryResult( String nameSpace , String fileName){
		
		boolean queryResult = true;
		
		NodeClient[] activeNodeArray = getActiveNodeInterface();
		
		for(NodeClient nc: activeNodeArray){

			String result = nc.checkDocQueryResponse(nameSpace, fileName);
			
			if(result.equalsIgnoreCase("Failure")){
				logger.info("Document upload validation failed for "+nameSpace+"/"+fileName);
				return false;
			}else if(result.equalsIgnoreCase("NA"))
				logger.warn("No response from node "+nc.getNodeId()+"for document upload validation for "+nameSpace+"/"+fileName);
				
		}
			return queryResult;
	}

	public static List fetchNamespaceList(String namespace){
		
		List fileList= new ArrayList();
		
		NodeClient[] activeNodeArray = getActiveNodeInterface();
		
		for(NodeClient nc: activeNodeArray){
			fileList = nc.sendNamespaceList(namespace);
		}
		
		return fileList;
	}
	
}