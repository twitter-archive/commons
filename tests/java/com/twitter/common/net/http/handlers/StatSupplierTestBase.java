package com.twitter.common.net.http.handlers;

import java.util.List;
import java.util.Map;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import org.junit.Before;

import com.twitter.common.stats.Stat;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;

/**
 * @author William Farner
 */
public abstract class StatSupplierTestBase extends EasyMockTest {

  protected Supplier<Iterable<Stat<?>>> statSupplier;

  @Before
  public void statSupplierSetUp() {
    statSupplier = createMock(new Clazz<Supplier<Iterable<Stat<?>>>>() {});
  }

  protected void expectVarScrape(Map<String, Object> response) {
    List<Stat<?>> vars = Lists.newArrayList();
    for (Map.Entry<String, Object> entry : response.entrySet()) {
      Stat stat = createMock(Stat.class);
      expect(stat.getName()).andReturn(entry.getKey());
      expect(stat.read()).andReturn(entry.getValue());
      vars.add(stat);
    }

    expect(statSupplier.get()).andReturn(vars);
  }
}
