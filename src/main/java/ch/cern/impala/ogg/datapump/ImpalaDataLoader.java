package ch.cern.impala.ogg.datapump;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.cern.impala.ogg.datapump.impala.ColumnsMetadata;
import ch.cern.impala.ogg.datapump.impala.ITable;
import ch.cern.impala.ogg.datapump.impala.ImpalaClient;
import ch.cern.impala.ogg.datapump.oracle.ControlFile;
import ch.cern.impala.ogg.datapump.oracle.OracleClient;
import ch.cern.impala.ogg.datapump.utils.PropertiesE;

public class ImpalaDataLoader {
	
	final private static Logger LOG = LoggerFactory.getLogger(ImpalaDataLoader.class);

	/**
	 * Maximun milliseconds between batches
	 */
	private static final long MAX_MS_BETWEEN_BATCHES = 10 * 60 * 1000;

	public static void main(String[] args) throws Exception {
		
		//Load properties file
		String prop_file = args == null || args.length != 1 || args[0] == null ?
				PropertiesE.DEFAULT_PROPETIES_FILE : args[0];
		PropertiesE prop = new PropertiesE(prop_file);
		LOG.info("inicializing loader (properties file = " + prop_file +")");

		//Get control file which is generated by OGG
		ControlFile sourceControlFile = prop.getSourceContorlFile();
		LOG.info("reading control data from " + sourceControlFile);
		
		ImpalaClient impC = new ImpalaClient(prop.getImpalaHost(), prop.getImpalaPort());
		
		//Create target table if it does not exist
		ColumnsMetadata metadata = new OracleClient().getMetadata("lhclog.data_numeric_ogg");
		ITable targetTable = impC.createTable("lhclog", "data_numeric_ogg", metadata);
		
		//Period of time for checking new data
		long ms_between_batches = prop.getMsBetweenBatches();
		
		//Get file systems
		Configuration conf = new Configuration();
		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		FileSystem hdfs = FileSystem.get(conf);
		conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());
		FileSystem local = FileSystem.getLocal(conf);
		
		Path stagingHDFSDirectory = prop.getStagingHDFSDirectory();
		
		//Always checking periodically for new data
		while(true){
			long startTime = System.currentTimeMillis();
			
			//Control file which contains the list of files to process in this batch
			ControlFile controlFile = sourceControlFile.getControlFileToProcess();
			
			if(controlFile != null){
				LOG.info("there is new data to process");
				
				Batch batch = new Batch(local, hdfs, controlFile, targetTable, stagingHDFSDirectory);
				batch.start();
				batch.clean();
			}else{
				LOG.info("there is no data to process");
			}
			
			waitForNextBatch(startTime, ms_between_batches);
		}	
	}

	private static void waitForNextBatch(long startTime, long ms_between_batches) {
		long timeDiff = System.currentTimeMillis() - startTime;
		
		while(timeDiff < ms_between_batches){
			if(timeDiff > MAX_MS_BETWEEN_BATCHES){
				LOG.warn("the maximun time between batches (" 
						+ (ms_between_batches / 1000) +" seconds) has been achieved.");
				
				return;
			}
			
			timeDiff = System.currentTimeMillis() - startTime;
		}
	}

	
}
