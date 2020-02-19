package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.greaterThan;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class CqlQueryEqualityTests {
  @Test
  public void shouldNotBeEqualToAnotherObject() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(5), is(false));
  }

  @Test
  public void shouldNotBeEqualToDifferentQuery() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(greaterThan("foo", 10).value()), is(false));
  }

  @Test
  public void shouldNotBeEqualToNull() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(null), is(false));
  }

  @Test
  public void shouldBeEqualToItself() {
    final CqlQuery query = exactMatch("barcode", "12345").value();

    assertThat(query.equals(query), is(true));
  }

  @Test
  public void shouldBeEqualToQueryWithSameDefinition() {
    final CqlQuery firstQuery = exactMatch("barcode", "12345").value();
    final CqlQuery secondQuery = exactMatch("barcode", "12345").value();

    assertThat(firstQuery.equals(secondQuery), is(true));
  }
}
