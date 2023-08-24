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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tsurugidb.belayer.webapi.dto.ExecStatus;

public class FileWatcherTest {

    @Test
    public void testExecStatus() {
        var target = new FileWatcher(null);
        var json = "{\"timestamp\":1690440399, \"kind\":\"progress\", \"progress\":\"0.20\", \"message\":\"msg1\", \"code\":\"code1\", \"arguments\":[\"arg1\"]}";
        ExecStatus status = target.parseLine(json);

        assertEquals("[2023-07-27T06:46:39]progress:20.00%", status.toStatusString());
    }

}
