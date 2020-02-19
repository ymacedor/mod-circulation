package org.folio.circulation.support.http.client;

import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class PageLimitEqualityTests {
  @Test
  public void shouldNotBeEqualToAnotherObject() {
    final PageLimit limit = limit(5);

    assertThat(limit.equals(5), is(false));
  }

  @Test
  public void shouldNotBeEqualToDifferentLimit() {
    final PageLimit limit = limit(5);

    assertThat(limit.equals(limit(10)), is(false));
  }


  @Test
  public void shouldNotBeEqualToNull() {
    final PageLimit limit = limit(5);

    assertThat(limit.equals(null), is(false));
  }

  @Test
  public void shouldBeEqualToItself() {
    final PageLimit limit = limit(5);

    assertThat(limit.equals(limit), is(true));
  }

  @Test
  public void shouldBeEqualToLimitWithSameDefinition() {
    final PageLimit firstLimit = limit(5);
    final PageLimit secondLimit = limit(5);

    assertThat(firstLimit.equals(secondLimit), is(true));
  }
}
