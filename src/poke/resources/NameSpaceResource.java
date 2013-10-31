/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.management.loading.PrivateClassLoader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.management.HeartbeatManager;
import poke.server.nconnect.NodeResponseQueue;
import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;
import eye.Comm.Document;
import eye.Comm.Header;
import eye.Comm.NameSpace;
import eye.Comm.Payload;
import eye.Comm.PayloadReply;
import eye.Comm.Request;
import eye.Comm.Response;
import eye.Comm.Header.ReplyStatus;

public class NameSpaceResource implements Resource {
	protected static Logger logger = LoggerFactory.getLogger("NamespaceResource");
	private static final String HOMEDIR = "home";

	private static final String VISITORDIR = "away";

	private static final String NAMESPACEEXIST = " requested namespacce already exist:";
	private static final String NAMESPACENOTDIRECTORY = "Requested namespace is not a directory ";
	private static final String NAMESPACEINEXISTENTMSG = " Requested namespacce does not exist: Please provide valid namespace";
	private static final String INTERNALSERVERERRORMSG ="Failed to serve the request: Internal Server Error";
	private static final String NAMESPACEREMOVED = "Namespace removed successfully";
	private static final String NAMESPACEDOESNOTEXIST = "Namespace does not exist";
	 private static final String DOCLISTFOUND = "List of document was found";

	private static final File homeDir = new File(HOMEDIR);
	private static final File visitorDir = new File(VISITORDIR);


	@Override
	public Response process(Request request) {
		// TODO Auto-generated method stub

		int opChoice = 0;

		Response docOpResponse = null;

		Header docOpHeader = request.getHeader();

		Payload docOpBody =  request.getBody();

		opChoice = docOpHeader.getRoutingId().getNumber();

		switch(opChoice){
		case 10:
			docOpResponse = namespaceAdd(docOpHeader, docOpBody);
			break;
			
		case 11:
			docOpResponse = namespaceList(docOpHeader, docOpBody);
			break;

		case 13:
			docOpResponse = namespaceRemove(docOpHeader, docOpBody);
			break;	
		
		case 14:
			docOpResponse = namespaceQuery(docOpHeader, docOpBody);
			break;	
			
		case 15:
			docOpResponse = namespaceListQuery(docOpHeader, docOpBody);
			break;	


		default:
			System.out.println("NamespaceResource: No matching doc op id found for "+opChoice);
		}
		return docOpResponse;
	}

	private Response namespaceAdd(Header namespaceAddHeader, Payload namespaceAddBody) {
		// Add new namespace

		String nameSpace = namespaceAddBody.getSpace().getName();
		String namespacePath = HOMEDIR+File.separator+nameSpace;

		logger.info("namespace to be created "+nameSpace);

		File namespaceDir = new File(namespacePath);
		Response.Builder namespaceAddResponse = Response.newBuilder();
		Header.Builder namespaceAddHeaderBuilder = Header.newBuilder(namespaceAddHeader);
		boolean checkNamespace = false;

		try {
			FileUtils.forceMkdir(homeDir);
			checkNamespace = FileUtils.directoryContains(homeDir, namespaceDir);
			if(checkNamespace){
				logger.info("Namespace already exists");
				namespaceAddResponse.setHeader(ResourceUtil.buildHeaderFrom(namespaceAddHeader, ReplyStatus.FAILURE, NAMESPACEEXIST));

				return namespaceAddResponse.build();
			} else {
				try{
					logger.info("Creating directory with name "+nameSpace );
					FileUtils.forceMkdir(namespaceDir);
					namespaceAddHeaderBuilder.setReplyCode(Header.ReplyStatus.SUCCESS);
					namespaceAddHeaderBuilder.setReplyMsg("namespace created  Successfully");
				} catch (Exception e) {

					logger.warn("Exception while creating namespace "+ nameSpace+ " " + e.getMessage());

					namespaceAddHeaderBuilder.setReplyCode(Header.ReplyStatus.FAILURE);
					namespaceAddHeaderBuilder.setReplyMsg("Server Exception while creating namespace");
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			namespaceAddHeaderBuilder.setReplyCode(Header.ReplyStatus.FAILURE);
			namespaceAddHeaderBuilder.setReplyMsg("Server Exception while checking for namespace existance");
		}

		namespaceAddResponse.setHeader(namespaceAddHeaderBuilder);
		namespaceAddResponse.setBody(PayloadReply.newBuilder().build());

		return namespaceAddResponse.build();

	}


	private Response namespaceRemove(Header namespaceRemoveHeader, Payload namespaceRemoveBody) {
		// Remove the namespace

		String nameSpace = namespaceRemoveBody.getSpace().getName();

		logger.info("namespace to be deleted: "+nameSpace);

		Response.Builder namespaceRemoveResponseBuilder = Response.newBuilder();

		namespaceRemoveResponseBuilder.setBody(PayloadReply.newBuilder().build());

		if(nameSpace != null && nameSpace.length() > 0){

			String namespacePath = HOMEDIR+File.separator+nameSpace;

			File namespaceDir = new File (namespacePath);

			try {

				boolean checkNamespace = FileUtils.directoryContains(homeDir, namespaceDir);

				if(checkNamespace){

					if(namespaceDir.isDirectory()){
						FileUtils.forceDelete(namespaceDir);

					}
					else{
						namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, NAMESPACENOTDIRECTORY+"Supplied namespace is not directory"));
						//return namespaceRemoveResponseBuilder.build();
					}

				}else{
					namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, NAMESPACEINEXISTENTMSG));

					//return namespaceRemoveResponseBuilder.build();
				}
				// remove replica namespace
				
				String namespaceReplPath = VISITORDIR+File.separator+nameSpace;

				File namespaceReplDir = new File (namespaceReplPath);
				
				boolean checkReplNamespace = FileUtils.directoryContains(visitorDir, namespaceReplDir);

				if(checkReplNamespace){

					if(namespaceReplDir.isDirectory()){
						FileUtils.forceDelete(namespaceReplDir);

					}
					else{
						namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, NAMESPACENOTDIRECTORY+"Supplied namespace is not directory"));
					//	return namespaceRemoveResponseBuilder.build();
					}

				}else{
					namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, NAMESPACEINEXISTENTMSG));

				//	return namespaceRemoveResponseBuilder.build();
				}
			}
			catch(Exception e){
				
				logger.error("Namespace Response: IO Exception while processing namespace delete request "+e.getMessage());
				namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, INTERNALSERVERERRORMSG));

			//	return namespaceRemoveResponseBuilder.build();

			}

		}else{
			namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.FAILURE, INTERNALSERVERERRORMSG));

			return namespaceRemoveResponseBuilder.build();

		}
		
		NodeResponseQueue.broadcastNamespaceQuery(nameSpace);
		try{
		logger.info(" Document resousrce sleeping for 2000ms! Witing for responses from the other nodes for DOCQUERY ");
		
		Thread.sleep(2000);
		}
		catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		logger.info("Namespace successfully deleted");
		namespaceRemoveResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceRemoveHeader, ReplyStatus.SUCCESS, NAMESPACEREMOVED));

		return namespaceRemoveResponseBuilder.build();
	}
	
	private Response namespaceQuery(Header namespaceQueryHeader , Payload namespaceQueryBody){

		logger.info(" Received namespace query request from "+namespaceQueryHeader.getOriginator());

		Response.Builder namespaceQueryResponseBuilder = Response.newBuilder();

		NameSpace space = namespaceQueryBody.getSpace();
		namespaceQueryResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceQueryHeader, ReplyStatus.SUCCESS, NAMESPACEDOESNOTEXIST).toBuilder().setOriginator(HeartbeatManager.getInstance().getNodeId()));

		String nameSpace =  null;

		String effHomeNS = HOMEDIR;
		
		String effAwayNS = VISITORDIR;

		if(space !=null){
			nameSpace  = space.getName();
			namespaceQueryResponseBuilder.setBody(PayloadReply.newBuilder().addSpaces(space));
		}

		if(nameSpace !=null && nameSpace.length() >0){
			effHomeNS= effHomeNS+File.separator+nameSpace;
			effAwayNS = effAwayNS+File.separator+nameSpace;
		}


		File parentHomeDir = new File(effHomeNS);
		
		File parentAwayDir = new File(effAwayNS);

		try {

			if(parentHomeDir.exists()){
				FileUtils.forceDelete(parentHomeDir);
				logger.info("Deleted namespace in Home directory");
				namespaceQueryResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceQueryHeader, ReplyStatus.SUCCESS, NAMESPACEREMOVED).toBuilder().setOriginator(HeartbeatManager.getInstance().getNodeId()));

			}
			
			//if(!fileHome){
			if(parentAwayDir.exists()){
				FileUtils.forceDelete(parentAwayDir);
				logger.info("Deleted namespace in Replica directory");
				namespaceQueryResponseBuilder.setHeader(ResourceUtil.buildHeaderFrom(namespaceQueryHeader, ReplyStatus.SUCCESS, NAMESPACEREMOVED).toBuilder().setOriginator(HeartbeatManager.getInstance().getNodeId()));
			}

		} catch (IOException e) {

			logger.error("NamespaceQuery: IOException while deleting namespace");
			e.printStackTrace();
		}
		logger.info("sending response to namespace query's inbound queue");
		return namespaceQueryResponseBuilder.build();
	}
	
	private Response namespaceList(Header namespaceListHeader, Payload namespaceListBody) {
		// TODO Auto-generated method stub
		
		 boolean fileExists = false;
		 int index = 0;
         Response.Builder namespaceListResponse = Response.newBuilder();
         PayloadReply.Builder namespaceListRespBody = PayloadReply.newBuilder();
         Header.Builder namespaceListRespHeader = Header.newBuilder();
         String nameSpace = namespaceListBody.getSpace().getName();
         List<File> files = new ArrayList<File>();
		
         String namespacePath = HOMEDIR+File.separator+nameSpace;

			File namespaceDir = new File (namespacePath);
			String filename = null;
			String filePath = null;
			String fileExt = null;
			
			NodeResponseQueue.broadcastNamespaceListQuery(nameSpace);
			
			
			try {
				
				logger.info(" Document resousrce sleeping for 2000ms! Witing for responses from the other nodes for DOCQUERY ");
				
				Thread.sleep(2000);
				
			//	boolean docQueryResult = NodeResponseQueue.fetchDocQueryResult(nameSpace , newFileName);
				
				files = NodeResponseQueue.fetchNamespaceList(nameSpace);
				
				boolean checkNamespace = FileUtils.directoryContains(homeDir, namespaceDir);
				if(checkNamespace){
					
					files = (List<File>) FileUtils.listFiles(namespaceDir, TrueFileFilter.INSTANCE,
																		TrueFileFilter.INSTANCE);
					
					for (File file : files) {
						filename = file.getName();
						filePath = file.getCanonicalPath();
						fileExt = FilenameUtils.getExtension(filePath);
						namespaceListRespBody.addDocsBuilder();
						namespaceListRespBody.setDocs(index, Document.newBuilder()
											 .setDocName(filename).setDocExtension(fileExt));
						index++;
						

					}
					namespaceListResponse.setBody(namespaceListRespBody.build());
					namespaceListResponse.setHeader(ResourceUtil.buildHeaderFrom(namespaceListHeader, ReplyStatus.SUCCESS, DOCLISTFOUND));
					
				}
				else{
					namespaceListResponse.setBody(namespaceListRespBody.build());
					namespaceListResponse.setHeader(ResourceUtil.buildHeaderFrom(namespaceListHeader, ReplyStatus.FAILURE, NAMESPACEDOESNOTEXIST));
					
				}
			} catch (Exception e) {
				// TODO: handle exception
				logger.error("Error while processing request" +e.getMessage());
			}
         
	//	PayloadReply.Builder namespaceResBuilder = PayloadReply.newBuilder();
		
		
		
	//	namespaceResBuilder.getDocsList();
		
		
		return namespaceListResponse.build();
	}
	
	private Response namespaceListQuery(Header namespaceListQueryHeader, Payload namespaceListQueryBody) {
		// TODO Auto-generated method stub
		
		Response.Builder namespaceListQueryResponse = Response.newBuilder();
        PayloadReply.Builder namespaceListQueryRespBody = PayloadReply.newBuilder();

		
		NameSpace space = namespaceListQueryBody.getSpace();
		namespaceListQueryResponse.setHeader(ResourceUtil.buildHeaderFrom(namespaceListQueryHeader, ReplyStatus.SUCCESS, NAMESPACEDOESNOTEXIST).toBuilder().setOriginator(HeartbeatManager.getInstance().getNodeId()));
		
		String namespacePath = HOMEDIR+File.separator+space;
		int index = 0;
		File namespaceDir = new File (namespacePath);
		String filename = null;
		String filePath = null;
		String fileExt = null;
		
		try {
			boolean checkNamespace = FileUtils.directoryContains(homeDir, namespaceDir);
			if(checkNamespace){
				
				List<File> files = (List<File>) FileUtils.listFiles(namespaceDir, TrueFileFilter.INSTANCE,
																	TrueFileFilter.INSTANCE);
				
				for (File file : files) {
					filename = file.getName();
					filePath = file.getCanonicalPath();
					fileExt = FilenameUtils.getExtension(filePath);
					namespaceListQueryRespBody.addDocsBuilder();
					namespaceListQueryRespBody.setDocs(index, Document.newBuilder()
										 .setDocName(filename).setDocExtension(fileExt));
					index++;
				}
				namespaceListQueryResponse.setBody(namespaceListQueryRespBody.build());
				namespaceListQueryResponse.setHeader(ResourceUtil.buildHeaderFrom(namespaceListQueryHeader, ReplyStatus.SUCCESS, DOCLISTFOUND));
				
			}
		} catch (Exception e) {
			// TODO: handle exception
			logger.error("Error while processing request" +e.getMessage());
		}
     
		
		
		
		return namespaceListQueryResponse.build();
	}

}
