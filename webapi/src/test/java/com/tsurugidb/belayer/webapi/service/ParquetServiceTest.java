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

import org.junit.jupiter.api.Test;

public class ParquetServiceTest {

    ParquetService target = new ParquetService();

    private static final String TEST_PARQUET_TYPES = "./src/test/files/parquet/types.parquet";
    private static final String TEST_PARQUET_DECIMAL = "./src/test/files/parquet/decimals.parquet";
    private static final String TEST_CSV = "./src/test/files/parquet/type.csv";
    private static final String OUT_DIR = "./src/test/files/parquet/out/";

    // @Test
    public void test_getSchemaFromParquet() {

        var schema = target.getSchemaFromParquet(TEST_PARQUET_TYPES);
        System.out.println("schema:" + schema.toString());

    }

    @Test
    public void test_convertParquetToCsv() {

        target.convertParquetToCsv(TEST_PARQUET_TYPES, OUT_DIR + "/type.csv");

    }

    @Test
    public void test_convertParquetToCsv2() {

        target.convertParquetToCsv(TEST_PARQUET_DECIMAL, OUT_DIR + "/decimal.csv");

    }

    @Test
    public void test_convertCsvToParquet() {

        target.convertCsvToParquet(TEST_CSV, OUT_DIR + "/type.parquet");

    }

}
