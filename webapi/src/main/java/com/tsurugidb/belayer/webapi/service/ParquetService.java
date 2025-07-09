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
package com.tsurugidb.belayer.webapi.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroup;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeLogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimestampLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;
import org.apache.parquet.schema.Types.GroupBuilder;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.exception.IORuntimeException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ParquetService {

    /**
     * get metadata from parquet file.
     *
     * @param parquetFilePath file path of the input parquet file.
     */
    public MessageType getSchemaFromParquet(String parquetFilePath) {

        log.debug("parquetFilePath:{}", parquetFilePath);

        try (ParquetFileReader reader = ParquetFileReader
                .open(HadoopInputFile.fromPath(new Path(parquetFilePath), new Configuration()))) {

            // get metadata from footer
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            return schema;
        } catch (IOException ex) {
            throw new IORuntimeException("failed to get schema meatadata form parquet", ex);
        }
    }

    /**
     * convert parquet to csv.
     *
     * @param parquetFilePath file path of the input parquet file.
     * @param csvFilePath     file path of the output CSV file.
     */
    public void convertParquetToCsv(String parquetFilePath, String csvFilePath) {

        log.debug("parquetFilePath:{}, csvFilePath:{}", parquetFilePath, csvFilePath);

        try (ParquetFileReader reader = ParquetFileReader
                .open(HadoopInputFile.fromPath(new Path(parquetFilePath), new Configuration()));
                Writer writer = new FileWriter(csvFilePath);
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.POSTGRESQL_CSV);) {

            // header
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            log.debug("schema:" + schema);

            List<Type> fields = schema.getFields();
            for (Type type : fields) {
                printer.print(type.getName());
            }
            printer.println();

            // data
            PageReadStore pages;
            while ((pages = reader.readNextRowGroup()) != null) {

                MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));

                long rows = pages.getRowCount();
                for (int i = 0; i < rows; i++) {
                    SimpleGroup simpleGroup = (SimpleGroup) recordReader.read();

                    var type = simpleGroup.getType();
                    int fieldCount = type.getFieldCount();
                    for (int field = 0; field < fieldCount; field++) {
                        int valueCount = simpleGroup.getFieldRepetitionCount(field);
                        Type fieldType = type.getType(field);
                        String fieldName = fieldType.getName();

                        printer.print(getValue(simpleGroup, i, fieldName, valueCount));
                    }
                    printer.println();
                }
            }
        } catch (IOException ex) {
            throw new IORuntimeException("failed to convert parquet to csv", ex);
        }

    }

    protected String getValue(SimpleGroup simpleGourp, int row, String fieldName, int valueCount) {

        GroupType schema = simpleGourp.getType();
        int fieldCount = schema.getFieldIndex(fieldName);
        Type fieldType = schema.getType(fieldCount);
        PrimitiveType primitiveType = fieldType.asPrimitiveType();
        LogicalTypeAnnotation logicalType = fieldType.getLogicalTypeAnnotation();

        // treat as NULL when valueCount is 0
        if (valueCount == 0) {
            return null;
        }

        if (valueCount == 1) {

            // BOOLEAN
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.BOOLEAN) {
                return String.valueOf(simpleGourp.getBoolean(fieldCount, 0));
            }

            // INT
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.INT32
                    && logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                return String.valueOf(simpleGourp.getInteger(fieldCount, 0));
            }

            // BIGINT
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.INT64
                    && logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                return String.valueOf(simpleGourp.getLong(fieldCount, 0));
            }

            // FLOAT
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.FLOAT) {
                return String.valueOf(simpleGourp.getFloat(fieldCount, 0));
            }

            // DOUBLE
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.DOUBLE) {
                return String.valueOf(simpleGourp.getDouble(fieldCount, 0));
            }

            // STRING
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.BINARY
                    && logicalType == LogicalTypeAnnotation.stringType()) {
                return simpleGourp.getString(fieldCount, 0);
            }

            // DATE
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.INT32
                    && logicalType == LogicalTypeAnnotation.dateType()) {
                int daysFromEpoc = simpleGourp.getInteger(fieldCount, 0);

                var date = LocalDate.ofEpochDay(daysFromEpoc);
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }

            // TIME
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.INT64
                    && logicalType instanceof TimeLogicalTypeAnnotation) {
                TimeLogicalTypeAnnotation type = (TimeLogicalTypeAnnotation) logicalType;
                boolean adjustmentedToUtc = type.isAdjustedToUTC();
                TimeUnit unit = type.getUnit();
                if (unit != TimeUnit.NANOS) {
                    return "?";
                }

                long nanoSecFromMidNight = simpleGourp.getLong(fieldCount, 0);
                return toTimeString(nanoSecFromMidNight, adjustmentedToUtc, ZoneId.systemDefault());
            }

            // TIMESTAMP
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.INT64
                    && logicalType instanceof TimestampLogicalTypeAnnotation) {
                TimestampLogicalTypeAnnotation type = (TimestampLogicalTypeAnnotation) logicalType;
                boolean adjustmentedToUtc = type.isAdjustedToUTC();
                TimeUnit unit = type.getUnit();

                BigInteger nanoValue;
                if (unit == TimeUnit.MILLIS) {
                    long millValue = simpleGourp.getLong(fieldCount, 0);
                    nanoValue = BigInteger.valueOf(millValue).multiply(BigInteger.valueOf(1000000));
                } else if (unit == TimeUnit.MICROS) {
                    long microValue = simpleGourp.getLong(fieldCount, 0);
                    nanoValue = BigInteger.valueOf(microValue).multiply(BigInteger.valueOf(1000));
                } else if (unit == TimeUnit.NANOS) {
                    long nano = simpleGourp.getLong(fieldCount, 0);
                    nanoValue = BigInteger.valueOf(nano);
                } else {
                    return "?";
                }

                return toDateTimeString(nanoValue, adjustmentedToUtc, ZoneId.systemDefault());
            }

            // DECIMAL
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.BINARY &&
                    logicalType instanceof DecimalLogicalTypeAnnotation) {
                DecimalLogicalTypeAnnotation decimalType = ((DecimalLogicalTypeAnnotation) logicalType);
                int precision = decimalType.getPrecision();
                int scale = decimalType.getScale();
                log.debug("DECIMAL({},{})", precision, scale);
                Binary value = simpleGourp.getBinary(fieldCount, 0);
                return new BigDecimal(new BigInteger(value.getBytes()), scale).toPlainString();
            }

            // BINARY
            if (primitiveType.getPrimitiveTypeName() == PrimitiveTypeName.BINARY) {
                Binary value = simpleGourp.getBinary(fieldCount, 0);
                if (value == null) {
                    return null;
                }

                return Base64.getEncoder().encodeToString(value.getBytes());
            }

            log.debug("Single {}({}):{}", fieldName, fieldCount, fieldType);
            return "?";
        }

        for (int i = 0; i < valueCount; i++) {
            log.debug("Multi {}-{}[{}] {}", fieldName, simpleGourp.getType(), i,
                    simpleGourp.getValueToString(fieldCount, i));
        }

        return "?";
    }

    String toTimeString(long nanoSecFromMidNight, boolean adjustmentedToUtc, ZoneId zoneId) {

        if (adjustmentedToUtc) {
            BigInteger nanoValue = BigInteger.valueOf(nanoSecFromMidNight);
            var nanoUnit = BigInteger.valueOf(1000000000L);
            long epochSecond = nanoValue.divide(nanoUnit).longValue();
            long nanoSec = nanoValue.remainder(nanoUnit).longValue();

            // OffsetDateTime odt = OffsetDateTime.now(zoneId);
            // ZoneOffset offset = odt.getOffset();

            // This ALWAYS treats as UTC
            ZoneOffset offset = ZoneOffset.UTC;

            var timestamp = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nanoSec), offset);
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.nnnnnnnnn"));
        }

        LocalTime time = LocalTime.ofNanoOfDay(nanoSecFromMidNight);
        return time.format(DateTimeFormatter.ofPattern("HH:mm:ss.nnnnnnnnn"));

    }

    String toDateTimeString(BigInteger nanoValue, boolean adjustmentedToUtc, ZoneId zoneId) {

        var nanoUnit = BigInteger.valueOf(1000000000L);
        long epochSecond = nanoValue.divide(nanoUnit).longValue();
        long nanoSec = nanoValue.remainder(nanoUnit).longValue();

        if (adjustmentedToUtc) {
            // OffsetDateTime odt = OffsetDateTime.now(ZoneId.systemDefault());
            // ZoneOffset offset = odt.getOffset();

            // This ALWAYS treats as UTC
            ZoneOffset offset = ZoneOffset.UTC;
            var timestamp = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nanoSec), offset);
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn"));
        }

        var timestamp = LocalDateTime.ofEpochSecond(epochSecond, (int) nanoSec, ZoneOffset.UTC);
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn"));
    }

    /**
     * convert CSV to Parquet
     *
     * @param csvFilePath     file path of the input CSV file.
     * @param parquetFilePath file path of the output parquet file.
     */
    public void convertCsvToParquet(String csvFilePath, String parquetFilePath) {

        log.debug("parquetFilePath:{}, csvFilePath:{}", parquetFilePath, csvFilePath);

        var csvFile = new File(csvFilePath);

        var parquetFile = new File(parquetFilePath);
        if (parquetFile.exists())
            parquetFile.delete();

        CSVFormat csvFormat = CSVFormat.POSTGRESQL_CSV.builder().setHeader().build();

        try (CSVParser parser = CSVParser.parse(csvFile, StandardCharsets.UTF_8, csvFormat)) {

            List<String> cols = parser.getHeaderNames();
            log.debug("header names:{}", cols);

            MessageType schema = createStringOnlySchema(cols);
            Configuration configuration = new Configuration();
            configuration.setQuietMode(true);

            try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new Path(parquetFilePath))
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_1_0)
                    .withRowGroupSize((long) ParquetWriter.DEFAULT_BLOCK_SIZE)
                    .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE)
                    .withMaxPaddingSize(ParquetWriter.MAX_PADDING_SIZE_DEFAULT)
                    .withDictionaryPageSize(ParquetProperties.DEFAULT_DICTIONARY_PAGE_SIZE)
                    .withDictionaryEncoding(true)
                    .withValidation(false)
                    .withConf(configuration)
                    .withType(schema)
                    .build()) {

                for (CSVRecord record : parser.getRecords()) {
                    SimpleGroupFactory f = new SimpleGroupFactory(schema);
                    Group group = f.newGroup();
                    for (String col : cols) {
                        if (record.get(col) == null) {
                            continue;
                        }
                        group.add(col, record.get(col));
                    }
                    writer.write(group);
                }
            }
        } catch (IOException ex) {
            throw new IORuntimeException("failed to convert csv to parquet", ex);
        } finally {
            var path = java.nio.file.Path.of(parquetFilePath);
            var dir = path.getParent().toString();
            var parquetFileName = path.getFileName().toString();
            String crcFileName = "." + parquetFileName + ".crc";
            try {
                var crcFilePath = java.nio.file.Path.of(dir, crcFileName);
                boolean deleted = Files.deleteIfExists(crcFilePath);
                log.debug("deleted:{} file:{}", deleted, crcFilePath);
            } catch (IOException ignore) {
            }
        }

    }

    private MessageType createStringOnlySchema(List<String> cols) {
        GroupBuilder<MessageType> builder = Types.buildMessage();
        for (String col : cols) {
            builder = builder.addField(Types.primitive(PrimitiveTypeName.BINARY, Type.Repetition.OPTIONAL)
                    .as(LogicalTypeAnnotation.stringType()).named(col));
        }
        MessageType schema = builder.named("csv");
        return schema;
    }

}
