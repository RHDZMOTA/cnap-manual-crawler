# CNAP Policy Crawler

Application that extracts the CNAP policy directly from the [official website](https://dbmefaapolicy.azdes.gov) and
indexes the content using Solr. The text is storage in a Cassandra database. 

**Flow definition**
```text
urlStream ~> download ~> broadcast ~> extractRefs ~> recursiveStep
                         broadcast ~> solr
                         broadcast ~> cassandra 
```

Where:
```text
    urlStream   : an Akka actor that initializes the stream. 
    download    : performs a get request to extract the html content.
    extractRefs : extracts a list of uRL form the html.
    solr        : a Solr client that uploads the complete HTML. 
    cassandra   : a Cassandra client that persists uRL data. 
    recursiveStep : an Akka actor that performs validation and send a uRL into the urlStream if needed.
```

## Usage 

###Initial setup
1. Clone the repository: 
    * `git clone https://github.com/rhdzmota/cnap-policy-crawler.git`
2. Enter the base directory: 
    * `cd cnap-policy-crawler`
3. Create a config file: 
    * `cp src/main/resources/application.conf.example src/main/resources/application.conf`
4. Edit the config file: 
    * `vim src/main/resources/application.conf`
    

### Configuration file

You can find the configuration file at `src/main/resources/`. The main components are the following: 

```text
application = {
  source = {
    bufferSize = ""         // Buffer size of the stream (actor's mailbox: e.g., "1000000"). 
    overflowStrategy = ""   // Strategy to perform if bufferSize is reached (i.e., "fail", "dropBuffer", "dropHead", "dropTail")
  }
  crawler = {
    seedUrl = ""            // Initial uRL (i.e., "https://dbmefaapolicy.azdes.gov")
    urlLifeSpan = ""        // Lifespan in seconds of an uRL to avoid duplicates (e.g., "18000")
    parallelism = ""        // Parallel get requests (e.g., "30")
    depth = ""              // Max. depth for an uRL (e.g., "1000000")
  }
  cassandra = {
    address = ""            // Cassandra address (e.g., "127.0.0.1")
    port = ""               // Cassandra port (e.g., "9041")
    keyspaceName = ""       // Cassandra target keyspace (e.g., "cnap")
    urlTable = ""           // Cassandra target table (e.g., "policy")
    parallelism = ""        // Cassandra batch insert (e.g., "30")
  }
  solr = {
    address = ""            // Solr address (e.g., "127.0.0.1")
    port = ""               // Solr port (e.g., "8983")
    parallelism = ""        // Solr parallel requests (e.g., "30")
  }
}
```

Recommendations:
* A large **source bufferSize** is recommended in order to support the back pressure of the crawling (get requests)
and the storage. 
* A **parallelism** value of 30 is recommended for both Solr and the Crawler due to limitations with 
the connection pool. 
* A Cassandra batch insert (parallelism) value of 30 is recommended in order to match the parallelism 
value of the Crawler. 

### Run the application
1. Enter the base directory.
2. Run the application:
    * Using **sbt**: `sbt run`
    * Using **fat-jar**: compile using `sbt assembly` and run with `java -jar <path>`.
     
**Note** that you can find the fat-jar in the `target` directory. 

## Cassandra Setup

Create the keyspace (example).
```text
CREATE KEYSPACE cnap WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1};
```

Create the **policy table**:
```text
CREATE TABLE cnap.policy (
    id uuid, 
    uri text, 
    raw_body text, 
    breadcrumbs text, 
    body text, 
    depth int, 
    max_depth int, 
    from_url uuid, 
    crawl_job uuid, 
    timestamp timestamp,
    body text,
    PRIMARY KEY (id, from_url, crawl_job)
);
```

## Solr Setup
To be defined. 

## Authors 
To be defined. 

## License
To be defined.