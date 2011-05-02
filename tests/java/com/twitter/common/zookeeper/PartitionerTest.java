// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.zookeeper;

import com.google.common.testing.TearDown;
import com.twitter.common.zookeeper.Group.GroupChangeListener;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.Group.Membership;
import com.twitter.common.zookeeper.Partitioner.Partition;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class PartitionerTest extends BaseZooKeeperTest {
  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final String PARTITION_NAMESPACE = "/twitter/puffin/hosebird";

  @Test
  public void testHeterogeneousPartitionGroup() throws Exception {
    ZooKeeperClient zkClient = createZkClient();
    ZooKeeperUtils.ensurePath(zkClient, ACL, PARTITION_NAMESPACE + "/not-a-partition-node");
    Partitioner partitioner = new Partitioner(zkClient, ACL, PARTITION_NAMESPACE);
    join(partitioner);

    assertEquals("Expected Partitioner to be tolerant of foreign nodes",
        1, partitioner.getGroupSize());
  }

  private static class InstrumentedPartitioner extends Partitioner {
    private final AtomicInteger myViewOfGroupSize = new AtomicInteger();

    public InstrumentedPartitioner(ZooKeeperClient zkClient) throws IOException {
      super(zkClient, ACL, PARTITION_NAMESPACE);
    }

    @Override GroupChangeListener createGroupChangeListener(Membership membership) {
      final GroupChangeListener listener = super.createGroupChangeListener(membership);
      return new GroupChangeListener() {
        @Override public void onGroupChange(Iterable<String> memberIds) {
          listener.onGroupChange(memberIds);
          synchronized (myViewOfGroupSize) {
            myViewOfGroupSize.set(getGroupSize());
            myViewOfGroupSize.notify();
          }
        }
      };
    }

    public void observeGroupSize(int expectedSize) throws InterruptedException {
      while (expectedSize != myViewOfGroupSize.get()) {
        synchronized (myViewOfGroupSize) {
          myViewOfGroupSize.wait();
        }
      }
    }
  }

  @Test
  public void testJoin() throws Exception {
    // Test that the 1st member of the partition group owns the whole space.
    InstrumentedPartitioner firstPartitioner = new InstrumentedPartitioner(createZkClient());
    Partition firstPartition = join(firstPartitioner);

    assertTrue(firstPartition.isMember(0L));
    assertTrue(firstPartition.isMember(1L));
    assertTrue(firstPartition.isMember(2L));

    // Test that when additional members join partitions are added and existing partitions shrink.
    InstrumentedPartitioner secondPartitioner = new InstrumentedPartitioner(createZkClient());
    Partition secondPartition = join(secondPartitioner);

    firstPartitioner.observeGroupSize(2);

    assertTrue(firstPartition.isMember(0L));
    assertFalse(secondPartition.isMember(0L));

    assertFalse(firstPartition.isMember(1L));
    assertTrue(secondPartition.isMember(1L));

    assertTrue(firstPartition.isMember(2L));
    assertFalse(secondPartition.isMember(2L));

    InstrumentedPartitioner thirdPartitioner = new InstrumentedPartitioner(createZkClient());
    Partition thirdPartition = join(thirdPartitioner);

    firstPartitioner.observeGroupSize(3);
    secondPartitioner.observeGroupSize(3);

    assertTrue(firstPartition.isMember(0L));
    assertFalse(secondPartition.isMember(0L));
    assertFalse(thirdPartition.isMember(0L));

    assertFalse(firstPartition.isMember(1L));
    assertTrue(secondPartition.isMember(1L));
    assertFalse(thirdPartition.isMember(1L));

    assertFalse(firstPartition.isMember(2L));
    assertFalse(secondPartition.isMember(2L));
    assertTrue(thirdPartition.isMember(2L));

    assertTrue(firstPartition.isMember(3L));
    assertFalse(secondPartition.isMember(3L));
    assertFalse(thirdPartition.isMember(3L));

    // Test that members leaving the partition group results in the partitions being merged.
    firstPartition.cancel();

    secondPartitioner.observeGroupSize(2);
    thirdPartitioner.observeGroupSize(2);

    assertTrue(secondPartition.isMember(0L));
    assertFalse(thirdPartition.isMember(0L));

    assertFalse(secondPartition.isMember(1L));
    assertTrue(thirdPartition.isMember(1L));

    assertTrue(secondPartition.isMember(2L));
    assertFalse(thirdPartition.isMember(2L));

    thirdPartition.cancel();

    secondPartitioner.observeGroupSize(1);

    assertTrue(secondPartition.isMember(0L));
    assertTrue(secondPartition.isMember(1L));
    assertTrue(secondPartition.isMember(2L));
  }

  private Partition join(Partitioner partitioner) throws JoinException, InterruptedException {
    final Partition partition = partitioner.join();
    addTearDown(new TearDown() {
      @Override public void tearDown() throws JoinException {
        partition.cancel();
      }
    });
    return partition;
  }
}
