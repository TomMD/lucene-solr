/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.cloud;

import org.apache.solr.common.ParWork;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ZkConfigManager;
import org.apache.solr.common.cloud.ZooKeeperException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.ConfigSetProperties;
import org.apache.solr.core.ConfigSetService;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * SolrCloud ConfigSetService impl.
 */
public class CloudConfigSetService extends ConfigSetService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  private final ZkController zkController;

  public CloudConfigSetService(SolrResourceLoader loader, boolean shareSchema, ZkController zkController) {
    super(loader, shareSchema);
    this.zkController = zkController;
  }

  @Override
  public SolrResourceLoader createCoreResourceLoader(CoreDescriptor cd) {
    final String colName = cd.getCollectionName();

    // The configSet is read from ZK and populated.  Ignore CD's pre-existing configSet; only populated in standalone
    final String configSetName;
    try {
      configSetName = zkController.getZkStateReader().readConfigName(colName);
      cd.setConfigSet(configSetName);
    } catch (KeeperException ex) {
      throw new ZooKeeperException(SolrException.ErrorCode.SERVER_ERROR, "Trouble resolving configSet for collection " + colName + ": " + ex.getMessage());
    }

    return new ZkSolrResourceLoader(cd.getInstanceDir(), configSetName, parentLoader.getClassLoader(), zkController);
  }

  @Override
  protected NamedList loadConfigSetFlags(CoreDescriptor cd, SolrResourceLoader loader) {
      return ConfigSetProperties.readFromResourceLoader(loader, ".");
  }

  @Override
  protected Long getCurrentSchemaModificationVersion(String configSet, SolrConfig solrConfig, String schemaFile) {
    String zkPath = ZkConfigManager.CONFIGS_ZKNODE + "/" + configSet + "/" + schemaFile;
    Stat stat;
    try {
      stat = zkController.getZkClient().exists(zkPath, null);
    } catch (KeeperException e) {
      log.warn("Unexpected exception when getting modification time of {}", zkPath, e);
      return null; // debatable; we'll see an error soon if there's a real problem
    } catch (InterruptedException e) {
      ParWork.propegateInterrupt(e);
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
    }
    if (stat == null) { // not found
      return null;
    }
    return (long) stat.getVersion();
  }

  @Override
  public String configSetName(CoreDescriptor cd) {
    return "configset " + cd.getConfigSet();
  }
}
