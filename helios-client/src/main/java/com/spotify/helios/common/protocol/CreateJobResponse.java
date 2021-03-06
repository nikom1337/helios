/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.common.protocol;

import com.google.common.base.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spotify.helios.common.Json;

import java.util.List;

public class CreateJobResponse {

  public enum Status {
    OK,
    ID_MISMATCH,
    JOB_ALREADY_EXISTS,
    INVALID_JOB_DEFINITION,
    UNKNOWN_JOB,
    AMBIGUOUS_JOB_REFERENCE,
  }

  private final Status status;
  private final List<String> errors;
  private final String id;

  public CreateJobResponse(@JsonProperty("status") final Status status,
                           @JsonProperty("errors") final List<String> errors,
                           @JsonProperty("id") final String id) {
    this.status = status;
    this.errors = errors;
    this.id = id;
  }

  public Status getStatus() {
    return status;
  }

  public List<String> getErrors() {
    return errors;
  }

  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(getClass())
        .add("status", status)
        .add("errors", errors)
        .add("id", id)
        .toString();
  }

  public String toJsonString() {
    return Json.asStringUnchecked(this);
  }
}
