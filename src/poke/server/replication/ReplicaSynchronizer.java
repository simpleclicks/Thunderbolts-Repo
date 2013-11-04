package poke.server.replication;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.management.HeartbeatManager;
import poke.server.nconnect.NodeResponseQueue;
import poke.server.storage.jdbc.DatabaseStorage;

/**
 * @author Kaustubh
 *	Handles the replica synchronization after every startup
 *	to ensure the degree of replication in the network
 */
public class ReplicaSynchronizer extends Thread{

	static final private DatabaseStorage dbAct = DatabaseStorage.getInstance();
	static final private String self = HeartbeatManager.getInstance().getNodeId();
	protected static final Logger logger = LoggerFactory.getLogger("ReplicaSynchronizer ");
	private static final int MAXWAITFORRESPONSE = 6000;
	
	@Override
	public void run() {
		super.run();
		
		List<String> replicatedFiles = dbAct.getDocuments(self);
		
		if(replicatedFiles.size() == 0){
		
			logger.info("No replicas stored natively to intialize a sync up process");
			logger.info(" Aborting synchronization process...");
			return;
		
		}else{
			
			logger.info("Number of replicas to be synced up "+replicatedFiles.size());
			logger.info(" Initiating synchronization process...");
			
			for(String filePath: replicatedFiles){
				
				File replicaToBeDeleted = new File(filePath);

				
				String owner = dbAct.getOwner(FilenameUtils.getPath(filePath), FilenameUtils.getName(filePath));
				
				if(owner!= null && owner.length() > 0){
					
					logger.info(" Sent master-replica-query for "+filePath);
					boolean masterExists = NodeResponseQueue.masterReplicaQuery(filePath, owner);
					if(masterExists){
					
					logger.info(" Checking master-replica-query response for "+filePath);
					
					boolean masrterReplicaResult = NodeResponseQueue.fetchMasterReplicaQueryResult(filePath, owner);
					
					if(masrterReplicaResult){
						logger.warn(owner+" has re-replicated file to other nodes. Deleting native replica");
						replicaToBeDeleted.delete();
					}else{
						logger.warn(owner+" has not re-replicated file to other nodes. keeping native replica intact");
					}
						
					
					
					}else{
						broadcastReplicaQuery(filePath);
					}
					
				}else{
				
					broadcastReplicaQuery(filePath);
					
			  }// if-else ends or broadcast- multicast selection
			}
			logger.info(" Replica sync up process has been completed. Process exiting...");
		}
		
	}
	
	private void broadcastReplicaQuery(String filePath){
		
		NodeResponseQueue.broadcastReplicaQuery(filePath);
		logger.info(" Broadcasted replicaQuery for "+filePath);
		logger.info("Sleeping for configured wait time !!! Waiting for response for replicaQuery from network nodes ");
		
		try {
			Thread.sleep(MAXWAITFORRESPONSE);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		logger.info(" Validating response of replicaQuery for "+filePath+" for the existence of the surplus replica");
		
		boolean replicaQueryResult = NodeResponseQueue.fetchReplicaQueryResult(filePath);
		
		if(replicaQueryResult){
			logger.info(" Replica exists for "+filePath+" in the network. Proceeding to delete the native copy");
			File replicaToBeDeleted = new File(filePath);
			try{
			boolean opComplete = replicaToBeDeleted.delete();
			if(opComplete)
				logger.info("Replica "+filePath+" has been deleted successfully");
			else
				logger.warn("Replica "+filePath+" could not be deleted: Please retry ensuring file is not being accessed.");
			}catch(Exception e){
				logger.error(" Encountered exception while deleting "+filePath+" cause "+e.getMessage());
				e.printStackTrace();
			}
		}else{
			
			logger.info("Network does not have surplus replica of "+filePath+" keeping the native copy intact.");
		}
	}
}
