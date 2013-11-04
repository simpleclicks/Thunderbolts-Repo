package poke.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import eye.Comm.Document;
import eye.Comm.Header;
import eye.Comm.Header.ReplyStatus;
import eye.Comm.NameSpace;
import eye.Comm.Payload;
import eye.Comm.PayloadReply;
import eye.Comm.Request;
import eye.Comm.Response;
import poke.server.management.HeartbeatManager;
import poke.server.nconnect.NodeClient;
import poke.server.nconnect.NodeResponseQueue;
import poke.server.resources.ChunkedResource;
import poke.server.resources.ResourceUtil;
import poke.server.storage.jdbc.DatabaseStorage;

public class DocumentChunkResource implements ChunkedResource {
	
	 protected static Logger logger = LoggerFactory.getLogger("DocumentResource");

     private static final String HOMEDIR = "home";

     private static final String VISITORDIR = "away";
     
     private static final String DOCFOUNDSUCCESS = "Requested document was found successfully";

     private static final File homeDir = new File(HOMEDIR);
     
     private static final File visitorDir = new File(VISITORDIR);     

 	private static final int MAX_UNCHUNKED_FILE_SIZE = 26214400;

	private static final long MAXWAITFORRESPONSE = 3000;

	@Override
	public List<Response> process(Request request) {

		List<Response> responses = new ArrayList<Response>();
		int opChoice = 0;
		Header docOpHeader = request.getHeader();
		Payload docOpBody = request.getBody();
		opChoice = docOpHeader.getRoutingId().getNumber();

		switch (opChoice) {
		
		 case 21:
				 responses = docFind(docOpHeader, docOpBody); 
				 break;
             
			default:
				System.out
						.println("DocumentChunkResource: No matching doc op id found");

		}

		return responses;
	}

	private List<Response> docFind(Header docFindHeader, Payload docFindBody){
		
		List<Response> responses = new ArrayList<Response>();
		String fileName = HOMEDIR + File.separator
				+ docFindBody.getSpace().getName() + File.separator
				+ docFindBody.getDoc().getDocName();
		String fileNameAway = VISITORDIR + File.separator
				+ docFindBody.getSpace().getName() + File.separator
				+ docFindBody.getDoc().getDocName();
		boolean fileExists = false;
		boolean awayExists= false;
		Response.Builder docFindResponse = Response.newBuilder();
		PayloadReply.Builder docFindRespPayload = PayloadReply.newBuilder();
		Header.Builder docFindRespHeader = Header.newBuilder();
		String nameSpace = docFindBody.getSpace().getName();
		String self = HeartbeatManager.getInstance().getNodeId();
		docFindRespPayload.addDocsBuilder();
		docFindRespPayload.addSpacesBuilder();
		
		docFindRespPayload.setDocs(0, Document.newBuilder().setDocName(fileName));
		//if(nameSpace == null)
		docFindRespPayload.setSpaces(0, NameSpace.newBuilder().setName(nameSpace));
		
		try {
			fileExists = FileUtils.directoryContains(homeDir,
					new File(fileName));
			awayExists = FileUtils.directoryContains(visitorDir, new File(fileNameAway));
			if(awayExists){
				fileName = fileNameAway;
			}
			if (fileExists || awayExists) {
				responses = docFindClient(docFindHeader, responses, fileName,
						docFindResponse, docFindRespPayload, docFindRespHeader,
						nameSpace);

			} else if(!fileExists && docFindHeader.getOriginator().contains("Client")){
				//TODO broadcast to all nodes
				NodeResponseQueue.broadcastDocFind(nameSpace, docFindBody.getDoc().getDocName());
                
                try {

					logger.info(" DocQuery: sleeping for 3000ms! Waiting for responses from the other nodes for DOCQUERY ");

					Thread.sleep(MAXWAITFORRESPONSE);
					
					boolean docFindResult = NodeResponseQueue.fetchDocFindResult(nameSpace , docFindBody.getDoc().getDocName());

					if(docFindResult){
						String tempfname = "temp" + File.separator
						+ docFindBody.getSpace().getName() + File.separator
						+ docFindBody.getDoc().getDocName();
						responses = docFindClient(docFindHeader, responses, tempfname,
								docFindResponse, docFindRespPayload, docFindRespHeader,
								nameSpace);
						//docFindResponse.setHeader(ResourceUtil.buildHeaderFrom(docFindHeader, ReplyStatus.SUCCESS, "Document created in temp").toBuilder().setOriginator(self));

					}else{

						docFindResponse.setHeader(ResourceUtil.buildHeaderFrom(docFindHeader, ReplyStatus.FAILURE, "Document not found").toBuilder().setOriginator(self));
						 docFindResponse.setBody(docFindRespPayload.build());
			              
			             responses.add(docFindResponse.build());
					}

				} catch (InterruptedException e1) {

					e1.printStackTrace();
				}
			} else if(!fileExists && !docFindHeader.getOriginator().contains("Client")){
				 docFindRespHeader.setReplyCode(Header.ReplyStatus.FAILURE);

	             docFindRespHeader.setReplyMsg("Server could not find the file.");
	             docFindResponse.setHeader(ResourceUtil.buildHeaderFrom(docFindHeader, ReplyStatus.FAILURE, "Document not Found").toBuilder().setOriginator(self));
	             //docFindRespPayload.addSpacesBuilder();
	              docFindResponse.setBody(docFindRespPayload.build());
	              
	             responses.add(docFindResponse.build());
			}
			
		} catch (IOException e) {
			  logger.info("IO Exception while creating the file and/or writing the content to it "+e.getMessage());

              docFindRespHeader.setReplyCode(Header.ReplyStatus.FAILURE);

              docFindRespHeader.setReplyMsg("Server Exception while downloading the requested file.");
              docFindResponse.setHeader(docFindRespHeader.build());
              docFindResponse.setBody(docFindRespPayload.build());
              responses.add(docFindResponse.build());

              e.printStackTrace();
		}

		return responses;
	}

	private List<Response> docFindClient(Header docFindHeader, List<Response> responses,
			String fileName, Response.Builder docFindResponse,
			PayloadReply.Builder docFindRespPayload,
			Header.Builder docFindRespHeader, String nameSpace) {
		
		String fileExt = FilenameUtils.getExtension(fileName);

		java.io.File file = FileUtils.getFile(fileName);

		long fileSize = FileUtils.sizeOf(file);

		logger.info("Size of the file to be sent " + fileSize);

		long totalChunk = ((fileSize / MAX_UNCHUNKED_FILE_SIZE)) + 1;

		if (fileSize < MAX_UNCHUNKED_FILE_SIZE) {

			logger.info(" DocFind: Sending the complete file in unchunked mode");

			logger.info("Total number of chunks " + totalChunk);

			byte[] fileContents = null;

			try {

				fileContents = FileUtils.readFileToByteArray(file);

			} catch (IOException e) {

				logger.error("Error while reading the specified file "
						+ e.getMessage());
				docFindRespHeader.setReplyCode(Header.ReplyStatus.FAILURE);

				docFindRespHeader
						.setReplyMsg("Server Exception while downloading the file found.");
				docFindResponse.setBody(docFindRespPayload.build());
				docFindResponse.setHeader(docFindHeader);
				responses.add(docFindResponse.build());
				return responses;
			}
			
			docFindRespPayload.addDocsBuilder();

			docFindRespPayload.setDocs(0, Document.newBuilder().setDocName(fileName)
			.setDocExtension(fileExt)
			.setChunkContent(ByteString.copyFrom(fileContents))
			.setDocSize(fileSize).setTotalChunk(totalChunk)
			.setChunkId(1));

			docFindResponse.setBody(docFindRespPayload.build());
			docFindResponse.setHeader(ResourceUtil.buildHeaderFrom(docFindHeader, ReplyStatus.SUCCESS, DOCFOUNDSUCCESS));
			responses.add(docFindResponse.build());
			return responses;

		} else {

			logger.info(" DocFind: Uploading the file in chunked mode");

			logger.info("Total number of chunks " + totalChunk);

			try {

				int bytesRead = 0;

				int chunkId = 1;

				FileInputStream chunkeFIS = new FileInputStream(file);

				do {

					byte[] chunckContents = new byte[26214400];

					bytesRead = IOUtils.read(chunkeFIS, chunckContents, 0,
							26214400);

					logger.info("Total number of bytes read for chunk "
							+ chunkId + ": " + bytesRead);

					logger.info("Contents of the chunk "+chunkId+" : "+chunckContents);
					
					docFindRespPayload.addDocsBuilder();

					docFindRespPayload.setDocs(0,Document
							.newBuilder()
							.setDocName(fileName)
							.setDocExtension(fileExt)
							.setDocSize(fileSize).setTotalChunk(totalChunk)
							.setChunkId(chunkId));

					docFindResponse.setBody(docFindRespPayload.build());

					docFindResponse.setHeader(ResourceUtil.buildHeaderFrom(docFindHeader, ReplyStatus.SUCCESS, DOCFOUNDSUCCESS));

					//chunckContents = null;
					responses.add(docFindResponse.build());
					System.gc();

					chunkId++;

				} while (chunkeFIS.available() > 0);

				logger.info("Out of chunked write while loop");

			} catch (FileNotFoundException e) {

				logger.info("IO Exception while creating the file and/or writing the content to it "+e.getMessage());
				
				  docFindResponse.setBody(docFindRespPayload.build());
		          docFindRespHeader.setReplyCode(Header.ReplyStatus.FAILURE);

		          docFindRespHeader.setReplyMsg("Server Exception while downloading the requested file.");
		          
		          responses.add(docFindResponse.build());

		          e.printStackTrace();

			} catch (IOException e) {

				logger.info("IO Exception while creating the file and/or writing the content to it "+e.getMessage());
				
				docFindResponse.setBody(docFindRespPayload.build());

		          docFindRespHeader.setReplyCode(Header.ReplyStatus.FAILURE);

		          docFindRespHeader.setReplyMsg("Server Exception while downloading the requested file.");
		          
		          responses.add(docFindResponse.build());

		          e.printStackTrace();
			}
			return responses;
		}
	}

}