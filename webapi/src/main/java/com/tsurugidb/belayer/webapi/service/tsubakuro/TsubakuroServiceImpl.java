/*
 * Copyright 2023 tsurugi project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tsurugidb.belayer.webapi.service.tsubakuro;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

import org.apache.parquet.schema.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import com.tsurugidb.belayer.webapi.dto.BackupContext;
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.BackupTransaction;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.LoadColumnMapping;
import com.tsurugidb.belayer.webapi.dto.LoadFileInfo;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.TransactionalJob;
import com.tsurugidb.belayer.webapi.dto.TsurugiTransaction;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InterruptedRuntimeException;
import com.tsurugidb.belayer.webapi.exception.NoDataException;
import com.tsurugidb.belayer.webapi.exception.TimeoutRuntimeException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.LoadStatement;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.ParquetService;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;
import com.tsurugidb.belayer.webapi.util.FileUtil;
import com.tsurugidb.sql.proto.SqlRequest.Parameter;
import com.tsurugidb.sql.proto.SqlRequest.TransactionOption;
import com.tsurugidb.sql.proto.SqlRequest.TransactionType;
import com.tsurugidb.sql.proto.SqlRequest.WritePreserve;
import com.tsurugidb.tsubakuro.channel.common.connection.Credential;
import com.tsurugidb.tsubakuro.channel.common.connection.RememberMeCredential;
import com.tsurugidb.tsubakuro.common.Session;
import com.tsurugidb.tsubakuro.common.SessionBuilder;
import com.tsurugidb.tsubakuro.datastore.Backup;
import com.tsurugidb.tsubakuro.datastore.DatastoreClient;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.sql.PreparedStatement;
import com.tsurugidb.tsubakuro.sql.ResultSet;
import com.tsurugidb.tsubakuro.sql.SqlClient;
import com.tsurugidb.tsubakuro.sql.TableMetadata;
import com.tsurugidb.tsubakuro.sql.Transaction;
import com.tsurugidb.tsubakuro.sql.util.Load;
import com.tsurugidb.tsubakuro.sql.util.LoadBuilder;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Profile({ "default", "authmock" })
public class TsubakuroServiceImpl implements TsubakuroService {

  @Autowired
  FileSystemService fileSystemService;

  @Autowired
  ParquetService parquetService;

  @Value("${webapi.application.name}")
  private String applicationName;

  @Value("${webapi.tsurugi.url}")
  private String tsurugiUrl;

  @Value("${webapi.tsurugi.connect_timeout}")
  private int connectTimeout;

  @Value("${webapi.tsurugi.session_timeout}")
  private int sessionTimeout;

  @Value("${webapi.backup.progress_percentage_api_return}")
  private int backupProgressPercentageWhenApiRetern;

  @Value("${webapi.backup.progress_percentage_filesize_sum_computed}")
  private int backupProgressPercentageWhenSumComputed;

  @Value("${webapi.dump.progress_percentage_api_return}")
  private int dumpProgressPercentageWhenApiRetern;

  @Value("${webapi.dump.progress_percentage_filesize_sum_computed}")
  private int dumpProgressPercentageWhenSumComputed;

  public BackupJob createBackupTransaction(BackupJob job) {
    boolean green = false;

    Session session = null;
    DatastoreClient client = null;
    Backup backup = null;
    try {
      var dbEndpointUrl = Objects.requireNonNull(tsurugiUrl, "value is not supplied from ${webapi.tsurugi.url}");
      var cred = Objects.requireNonNull((String) job.getCredentials(), "credentials is not supplied");

      session = SessionBuilder.connect(dbEndpointUrl)
          .withCredential(new RememberMeCredential(cred))
          .withApplicationName(applicationName)
          .withLabel("backup")
          .create(this.connectTimeout, TimeUnit.SECONDS);

      Objects.requireNonNull(session);
      client = DatastoreClient.attach(session);
      backup = client.beginBackup().await();
      log.debug("expand session timeout for {} minutes", this.sessionTimeout);
      session.updateExpirationTime(this.sessionTimeout, TimeUnit.MINUTES).await();

      var tran = BackupTransaction.builder()
          .jobId(job.getJobId())
          .session(session)
          .datastoreClient(client)
          .backup(backup)
          .build();
      job.setBackupTransaction(tran);
      green = true;

      return job;

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to create backup transaction.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to create backup transaction.", ex);
    } catch (TimeoutException ex) {
      throw new TimeoutRuntimeException("failed to create backup transaction.", ex);
    } finally {
      if (!green) {
        if (session != null) {
          try {
            session.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
        if (client != null) {
          try {
            client.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
        if (backup != null) {
          try {
            backup.close();
          } catch (Exception ignore) {
            // ignore
          }
        }

      }
    }

  }

  @Override
  public Flux<BackupContext> backupOnline(BackupJob job) {

    log.debug("start getting file list by backup online.");

    BackupTransaction backupTran = job.getBackupTransaction();
    Objects.requireNonNull(backupTran);

    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    var files = backupTran.getBackupFilePaths(job, backupProgressPercentageWhenApiRetern,
        backupProgressPercentageWhenSumComputed);
    stopWatch.stop();
    log.debug("{}:{}ms", "backup.getFiles()", stopWatch.getTotalTimeMillis());

    return Flux.fromIterable(files);
  }

  public DumpJob createDumpTransaction(DumpJob job) {
    var credentials = Objects.requireNonNull((String) job.getCredentials(), "credentials is not supplied");

    var cred = new RememberMeCredential(credentials);
    var tx = createTransaction(TransactionType.READ_ONLY, cred, job.getJobId(), "dump", Optional.empty(), true, job.getTable());
    job.setTsurugiTransaction(tx);

    return job;
  }

  public LoadJob createLoadTransaction(LoadJob job) {
    var credentials = (String) job.getCredentials();
    Objects.requireNonNull(credentials);
    var cred = new RememberMeCredential(credentials);
    var tx = createTransaction(TransactionType.LONG, cred, job.getJobId(), "load", Optional.empty(), job.isTransactionNeeded(),
        job.getTable());
    job.setTsurugiTransaction(tx);

    return job;
  }

  public TsurugiTransaction createSqlClient(@Nonnull Credential credential, String jobId,
      Optional<Integer> timeoutMin, String label) {

    Session session = null;
    SqlClient client = null;
    boolean green = false;
    try {
      var dbEndpointUrl = Objects.requireNonNull(tsurugiUrl, "value is not supplied from ${webapi.tsurugi.url}");

      session = SessionBuilder.connect(dbEndpointUrl)
          .withCredential(credential)
          .withApplicationName(applicationName)
          .withLabel(label)
          .create(this.connectTimeout, TimeUnit.SECONDS);

      Integer sessionTimeout = timeoutMin.orElse(Integer.valueOf(this.sessionTimeout));
      log.debug("expand session timeout for {} minutes", sessionTimeout);
      session.updateExpirationTime(sessionTimeout.intValue(), TimeUnit.MINUTES).await();
      client = SqlClient.attach(session);

      var tranObject = TsurugiTransaction.builder()
          .jobId(jobId)
          .session(session)
          .sqlClient(client)
          .build();
      green = true;
      return tranObject;

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to create tsurugi SQL client.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to create tsurugi SQL client.", ex);
    } catch (TimeoutException ex) {
      throw new TimeoutRuntimeException("failed to create tsurugi SQL client.", ex);
    } finally {
      if (!green) {
        if (session != null) {
          try {
            session.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
        if (client != null) {
          try {
            client.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
      }
    }
  }

  public TsurugiTransaction createTransaction(TransactionType transactionType, @Nonnull Credential credential,
      String jobId, String label,
      Optional<Integer> timeoutMin, boolean needTransaction, String... tables) {
    boolean green = false;

    TsurugiTransaction tsurugiTransaction = null;
    Transaction tran = null;
    try {
      tsurugiTransaction = createSqlClient(credential, jobId, timeoutMin, label);
      if (!needTransaction) {
        green = true;
        return tsurugiTransaction;
      }

      var optsBuilder = TransactionOption.newBuilder().setType(transactionType);
      optsBuilder.setLabel(label);
      if (transactionType == TransactionType.LONG) {
        if (tables == null) {
          String message = "Write target tables are not specified.";
          throw new BadRequestException(message, message);
        }
        for (String table : tables) {
          if (table.length() > 0) {
            var tableWritePreserve = WritePreserve.newBuilder().setTableName(table).build();
            optsBuilder = optsBuilder.addWritePreserves(tableWritePreserve);
          }
        }
      }

      var opts = optsBuilder.build();
      Objects.requireNonNull(opts);
      tran = tsurugiTransaction.getSqlClient().createTransaction(opts).await();
      tsurugiTransaction.setTransaction(tran);
      green = true;

      return tsurugiTransaction;

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to create tsurugi transaction.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to create tsurugi transaction.", ex);
    } finally {
      if (!green) {
        if (tran != null) {
          try {
            tran.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
        if (tsurugiTransaction != null) {
          try {
            tsurugiTransaction.close();
          } catch (Exception ignore) {
            // ignore
          }
        }
      }
    }
  }

  /**
   * dump all rows in specified table.
   *
   * @param job DumpJob
   * @return absolute path list of generated dump files.
   */
  public Flux<Path> dumpTable(DumpJob job) {

    Path destDir;
    try {
      destDir = fileSystemService.convertToAbsolutePath(job.getUid(), job.getDirPath() + "/" + job.getJobId());
      Files.createDirectories(destDir);
    } catch (IOException ex) {
      throw new IORuntimeException("Can't create output directory", ex);
    }

    // Parquet: create dump file directly in the destination directory.
    // CSV: create dump in tmp directory and convert to CSV in destination directory
    // in parallel.
    Path outDir;
    if (job.isOutputTempDir()) {
      try {
        Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_DUMP + job.getJobId());
        job.setTempDir(tmpDir);
        outDir = tmpDir;

      } catch (IOException ex) {
        throw new IORuntimeException("Can't create temp directory", ex);
      }
    } else {
      outDir = destDir;
    }

    SqlClient client = job.getTsurugiTransaction().getSqlClient();

    Flux<Path> flux = Flux.create((sink) -> {

      PreparedStatement prep = null;
      ResultSet results = null;
      try {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        prep = client.prepare("SELECT * FROM " + job.getTable()).await();
        Transaction tx = job.getTsurugiTransaction().getTransaction();

        List<Parameter> emptyParam = List.of();

        Objects.requireNonNull(prep);
        Objects.requireNonNull(outDir);
        Objects.requireNonNull(emptyParam);
        results = tx.executeDump(prep, emptyParam, outDir).await();

        // save progress(API return)
        job.setProgress(dumpProgressPercentageWhenApiRetern);

        int count = 0;

        var list = new ArrayList<Path>();
        long fileSizeSum = 0;
        while (results.nextRow()) {
          if (results.nextColumn()) {
            count++;
            var s = results.fetchCharacterValue();
            var dumpFile = Paths.get(s);
            long fileSize = FileUtil.getFileSize(dumpFile);
            log.debug("file size:" + fileSize);

            fileSizeSum += fileSize;
            list.add(dumpFile);
          }
        }

        // save progress(File Sum computed)
        int rate = dumpProgressPercentageWhenSumComputed;

        log.debug("file size sum:" + fileSizeSum);
        long denominator = BigDecimal.valueOf(fileSizeSum)
            .divide(BigDecimal.valueOf(100 - rate).divide(BigDecimal.valueOf(100)), 2, RoundingMode.UP).longValue();
        long numerator = BigDecimal.valueOf(denominator).multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(100), new MathContext(0, RoundingMode.DOWN)).longValue();
        log.debug("num/dnm={}/{}", numerator, denominator);

        job.setProgressDenominator(denominator);
        job.addProgressNumerator(numerator);

        for (var filePath : list) {
          sink.next(filePath);
        }

        stopWatch.stop();
        log.debug("{}:{}ms", "dump.getFiles()", stopWatch.getTotalTimeMillis());

        if (count == 0) {
          sink.error(new NoDataException("No data found in the table '" + job.getTable() + "'."));
        }

        Session session = job.getTsurugiTransaction().getSession();
        log.debug("expand session timeout for {} minutes", this.sessionTimeout);
        session.updateExpirationTime(this.sessionTimeout, TimeUnit.MINUTES).await();

        sink.complete();
      } catch (IOException | ServerException ex) {
        throw new IORuntimeException("failed to dump.", ex);
      } catch (InterruptedException ex) {
        throw new InterruptedRuntimeException("failed to dump.", ex);
      } finally {
        if (prep != null) {
          try {
            prep.close();
          } catch (Exception ignore) {
            log.warn("failed to close PreparedStatement.", ignore);
            // ignore
          }
        }
        if (results != null) {
          try {
            results.close();
          } catch (Exception ignore) {
            log.warn("failed to close ResultSet.", ignore);
            // ignore
          }
        }
      }
    });

    return flux;
  }

  public Mono<String> loadFile(LoadJob job, LoadFileInfo loadFileInfo) {

    log.debug("loadFile():{}", loadFileInfo);

    try {
      StopWatch stopWatch = new StopWatch();
      stopWatch.start();

      Path dumpFilePath = loadFileInfo.getFilePath();
      Load load = getLoad(job, dumpFilePath, loadFileInfo.isFromCsv());

      TsurugiTransaction tran = job.getTsurugiTransaction();

      if (job.isTransactionNeeded()) {
        var tx = tran.getTransaction();
        Objects.requireNonNull(tx, "transaction is not supplied.");
        load.submit(tx, dumpFilePath).await();
      } else {
        var client = tran.getSqlClient();
        Objects.requireNonNull(client);
        load.submit(client, dumpFilePath).await();
      }

      stopWatch.stop();
      log.debug("{}:{}ms", "load", stopWatch.getTotalTimeMillis());

      Path downloadPath = fileSystemService.convertToDownloadPath(job.getUid(),
          loadFileInfo.getOriginalFilePath().toString());

      // set progress
      job.addProgressNumerator(FileUtil.getFileSize(dumpFilePath));

      Session session = job.getTsurugiTransaction().getSession();
      log.debug("expand session timeout for {} minutes", this.sessionTimeout);
      session.updateExpirationTime(this.sessionTimeout, TimeUnit.MINUTES).await();

      return Mono.just(downloadPath.toString());

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to load the dump file.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to load the dump file.", ex);
    }
  }

  protected Load getLoad(LoadJob job, Path dumpFilePath, boolean fromCsv) {
    synchronized (job) {
      Load load = job.getLoad();
      if (load != null) {
        return load;
      }

      log.debug("create Load object for job {}", job.getJobId());

      TsurugiTransaction tran = job.getTsurugiTransaction();
      String table = job.getTable();

      MessageType schema = parquetService.getSchemaFromParquet(dumpFilePath.toString());

      var client = tran.getSqlClient();

      boolean green = false;
      try {
        Objects.requireNonNull(table);
        TableMetadata tableMd = client.getTableMetadata(table).await();

        LoadStatement statement = LoadStatement.builder()
            .mappings(job.getMappings())
            .tableName(table)
            .tableMetadata(tableMd)
            .schema(schema)
            .build();

        Objects.requireNonNull(tableMd);
        LoadBuilder builder = LoadBuilder.loadTo(tableMd)
            .style(LoadBuilder.Style.OVERWRITE);
        for (LoadColumnMapping mapping : statement.getColMapping()) {
          if (fromCsv) {
            builder = builder.mapping(mapping.getTableColumn(), mapping.getParquetColumn(), String.class);
          } else {
            log.debug("mapping to:{}, from:{}", mapping.getTableColumn(), mapping.getParquetColumn());
            builder = builder.mapping(mapping.getTableColumn(), mapping.getParquetColumn());
          }
        }

        load = builder.build(client).await();

        // cache in Job and close Load in Job#close()
        job.setLoad(load);

        green = true;

        return load;

      } catch (IOException | ServerException ex) {
        throw new IORuntimeException("failed to load the dump file.", ex);
      } catch (InterruptedException ex) {
        throw new InterruptedRuntimeException("failed to load the dump file.", ex);
      } finally {
        // close load if load creation is failed.
        if (!green) {
          if (load != null) {
            try {
              load.close();
            } catch (Exception ignore) {
              // ignore
            }
          }
        }
      }
    }
  }

  public TransactionalJob commitTx(TransactionalJob job) {

    try {
      var tran = job.getTsurugiTransaction();
      if (tran != null && tran.getTransaction() != null) {
        tran.getTransaction().commit().await();
      }

      return job;

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to commit transaction.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to commit transaction.", ex);
    }
  }

  public TransactionalJob rollbackTx(TransactionalJob job) {

    try {
      var tran = job.getTsurugiTransaction();
      if (tran != null && tran.getTransaction() != null) {
        tran.getTransaction().rollback().await();
      }
    } catch (Exception ignore) {
      log.warn("rollback failed. ignore this.", ignore);
    }
    return job;
  }

  public List<String> listTables(String credential) {

    Session session = null;
    SqlClient client = null;
    try {
      var dbEndpointUrl = Objects.requireNonNull(tsurugiUrl, "value is not supplied from ${webapi.tsurugi.url}");

      Objects.requireNonNull(credential);
      session = SessionBuilder.connect(dbEndpointUrl)
          .withCredential(new RememberMeCredential(credential))
          .withApplicationName(applicationName)
          .withLabel("list_table")
          .create(this.connectTimeout, TimeUnit.SECONDS);

      Objects.requireNonNull(session);
      client = SqlClient.attach(session);

      var tableList = client.listTables().await();
      return tableList.getTableNames();

    } catch (IOException | ServerException ex) {
      throw new IORuntimeException("failed to create tsurugi SQL client.", ex);
    } catch (InterruptedException ex) {
      throw new InterruptedRuntimeException("failed to create tsurugi SQL client.", ex);
    } catch (TimeoutException ex) {
      throw new TimeoutRuntimeException("failed to create tsurugi SQL client.", ex);
    } finally {
      if (session != null) {
        try {
          session.close();
        } catch (Exception ignore) {
          // ignore
        }
      }
      if (client != null) {
        try {
          client.close();
        } catch (Exception ignore) {
          // ignore
        }
      }
    }
  }

}
