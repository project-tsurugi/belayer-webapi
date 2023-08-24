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
package com.tsurugidb.belayer.webapi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.parquet.schema.MessageType;

import com.tsurugidb.belayer.webapi.dto.ColumnMapping;
import com.tsurugidb.belayer.webapi.dto.LoadColumnMapping;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.sql.proto.SqlCommon.Column;
import com.tsurugidb.tsubakuro.sql.TableMetadata;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Builder
@ToString
@EqualsAndHashCode
@Slf4j
public class LoadStatement {

  private List<ColumnMapping> mappings;
  private String tableName;
  private TableMetadata tableMetadata;
  private MessageType schema;

  public List<LoadColumnMapping> getColMapping() {

    // if mapping is not specified
    if (mappings == null || mappings.size() == 0) {
      List<LoadColumnMapping> list = new ArrayList<>();
      for (Column tableCol : tableMetadata.getColumns()) {
        if (schema.getFieldIndex(tableCol.getName()) < 0) {
          log.debug("ignore column in parquet:{}", tableCol);
          continue;
        }
        var name = tableCol.getName();
        Objects.requireNonNull(name);
        list.add(new LoadColumnMapping(tableCol, name));
      }
      return list;
    }

    List<LoadColumnMapping> list = new ArrayList<>();
    for (ColumnMapping mapping : mappings) {
      String targetCol = mapping.getTargetColumn();
      Column tableCol = convertTableName(targetCol);

      String sourceCol = mapping.getSourceColumn();
      String parquetCol = convertParquetName(sourceCol);

      Objects.requireNonNull(tableCol);
      Objects.requireNonNull(parquetCol);
      list.add(new LoadColumnMapping(tableCol, parquetCol));
    }
    return list;
  }

  private Column convertTableName(String mappingExpression) {
    if (mappingExpression.startsWith("@")) {
      try {

        int index = Integer.parseInt(mappingExpression.substring(1));
        if (index >= tableMetadata.getColumns().size()) {
          String message = "Invalid column mapping definition. Column Index is out of bounds. definition:"
              + mappingExpression;
          throw new BadRequestException(message, message);
        }
        return tableMetadata.getColumns().get(index);
      } catch (NumberFormatException ex) {
        String message = "Invalid column mapping definition. Column Index is not numeric. definition:"
            + mappingExpression;
        throw new BadRequestException(message, message);
      }
    }

    // check if exists
    String colName = mappingExpression;
    Optional<? extends Column> target = tableMetadata.getColumns().stream().filter(col -> col.getName().equals(colName)).findFirst();
    if (target.isEmpty()) {
      String message = "Invalid column mapping definition. Column does not exists in the table. definition:"
          + mappingExpression;
      throw new BadRequestException(message, message);
    }

    return target.get();
  }

  private String convertParquetName(String mappingExpression) {
    if (mappingExpression.startsWith("@")) {
      try {
        int index = Integer.parseInt(mappingExpression.substring(1));
        if (index >= schema.getFieldCount()) {
          String message = "Invalid column mapping definition. Column Index is out of bounds. definition:"
              + mappingExpression;
          throw new BadRequestException(message, message);
        }
        return schema.getFieldName(index);
      } catch (NumberFormatException ex) {
        String message = "Invalid column mapping definition. Column Index is not numeric. definition:"
            + mappingExpression;
        throw new BadRequestException(message, message);
      }
    }

    String colName = mappingExpression;
    int index = schema.getFieldIndex(colName);
    if (index < 0) {
      String message = "Invalid column mapping definition. Column does not exists in the parquet schema. definition:"
          + mappingExpression;
      throw new BadRequestException(message, message);
    }

    return colName;
  }
}
