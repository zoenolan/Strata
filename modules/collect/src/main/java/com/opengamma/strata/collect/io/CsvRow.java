/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.collect.io;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A row in a CSV file.
 * <p>
 * Represents a single row in a CSV file, accessed via {@link CsvFile}.
 * Each row object provides access to the data in the row by field index.
 * If the CSV file has headers, the headers can also be used to lookup the fields.
 */
public final class CsvRow {

  /**
   * The header row, ordered as the headers appear in the file.
   */
  private final ImmutableList<String> headers;
  /**
   * The header map, transformed for case-insensitive searching.
   */
  private final ImmutableMap<String, Integer> searchHeaders;
  /**
   * The fields in the row.
   */
  private final ImmutableList<String> fields;

  //------------------------------------------------------------------------
  /**
   * Creates an instance, specifying the headers and row.
   * <p>
   * See {@link CsvFile}.
   * 
   * @param headers  the headers
   * @param fields  the fields
   */
  private CsvRow(ImmutableList<String> headers, ImmutableList<String> fields) {
    this.headers = headers;
    // need to allow duplicate headers and only store the first instance
    Map<String, Integer> searchHeaders = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String searchHeader = headers.get(i).toLowerCase(Locale.ENGLISH);
      searchHeaders.putIfAbsent(searchHeader, i);
    }
    this.searchHeaders = ImmutableMap.copyOf(searchHeaders);
    this.fields = fields;
  }

  /**
   * Creates an instance, specifying the headers and row.
   * <p>
   * See {@link CsvFile}.
   * 
   * @param headers  the headers
   * @param searchHeaders  the search headers
   * @param fields  the fields
   */
  CsvRow(ImmutableList<String> headers, ImmutableMap<String, Integer> searchHeaders, ImmutableList<String> fields) {
    
    this.headers = headers;
    this.searchHeaders = searchHeaders;
    this.fields = fields;
  }

  //------------------------------------------------------------------------
  /**
   * Gets the header row.
   * <p>
   * If there is no header row, an empty list is returned.
   * 
   * @return the header row
   */
  public ImmutableList<String> headers() {
    return headers;
  }

  /**
   * Gets all fields in the row.
   * 
   * @return the fields
   */
  public ImmutableList<String> fields() {
    return fields;
  }

  /**
   * Gets the number of fields.
   * 
   * @return the number of fields
   */
  public int fieldCount() {
    return fields.size();
  }

  /**
   * Gets the specified field.
   * 
   * @param index  the field index
   * @return the field
   * @throws IndexOutOfBoundsException if the field index is invalid
   */
  public String field(int index) {
    return fields.get(index);
  }

  /**
   * Gets a single field value from the row by header.
   * <p>
   * This returns the value of the first column where the header matches the specified header.
   * Matching is case insensitive.
   * 
   * @param header  the column header
   * @return the trimmed field value
   * @throws IllegalArgumentException if the header is not found
   */
  public String getField(String header) {
    return findField(header)
        .orElseThrow(() -> new IllegalArgumentException("Header not found: " + header));
  }

  /**
   * Gets a single field value from the row by header.
   * <p>
   * This returns the value of the first column where the header matches the specified header.
   * Matching is case insensitive.
   * 
   * @param header  the column header
   * @return the trimmed field value, empty if not found
   */
  public Optional<String> findField(String header) {
    return Optional.ofNullable(searchHeaders.get(header.toLowerCase(Locale.ENGLISH)))
        .map(idx -> fields.get(idx));
  }

  /**
   * Gets a single field value from the row by header pattern.
   * <p>
   * This returns the value of the first column where the header matches the specified header pattern.
   * 
   * @param headerPattern  the header pattern to match
   * @return the trimmed field value, empty
   * @throws IllegalArgumentException if the header is not found
   */
  public String getField(Pattern headerPattern) {
    return findField(headerPattern)
        .orElseThrow(() -> new IllegalArgumentException("Header pattern not found: " + headerPattern));
  }

  /**
   * Gets a single field value from the row by header pattern.
   * <p>
   * This returns the value of the first column where the header matches the specified header pattern.
   * 
   * @param headerPattern  the header pattern to match
   * @return the trimmed field value, empty if not found
   */
  public Optional<String> findField(Pattern headerPattern) {
    for (int i = 0; i < headers.size(); i++) {
      if (headerPattern.matcher(headers.get(i)).matches()) {
        return Optional.of(fields.get(i));
      }
    }
    return Optional.empty();
  }

  //-------------------------------------------------------------------------
  /**
   * Obtains a sub-row, containing a selection of fields by index.
   * <p>
   * All fields after the specified index are included.
   * 
   * @param startInclusive  the start index, inclusive
   * @return the sub row
   */
  public CsvRow subRow(int startInclusive) {
    return subRow(startInclusive, fields.size());
  }

  /**
   * Obtains a sub-row, containing a selection of fields by index.
   * 
   * @param startInclusive  the start index, inclusive
   * @param endExclusive  the end index, exclusive
   * @return the sub row
   */
  public CsvRow subRow(int startInclusive, int endExclusive) {
    return new CsvRow(
        headers.subList(Math.min(startInclusive, headers.size()), Math.min(endExclusive, headers.size())),
        fields.subList(startInclusive, endExclusive));
  }

  //-------------------------------------------------------------------------
  /**
   * Checks if this CSV file equals another.
   * <p>
   * The comparison checks the content.
   * 
   * @param obj  the other file, null returns false
   * @return true if equal
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof CsvRow) {
      CsvRow other = (CsvRow) obj;
      return headers.equals(other.headers) && fields.equals(other.fields);
    }
    return false;
  }

  /**
   * Returns a suitable hash code for the CSV file.
   * 
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return headers.hashCode() ^ fields.hashCode();
  }

  /**
   * Returns a string describing the CSV file.
   * 
   * @return the descriptive string
   */
  @Override
  public String toString() {
    return "CsvRow" + fields.toString();
  }

}
