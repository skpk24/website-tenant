/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.tenant.tenant;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.FileUtil;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.datasource.GenericHelperInfo;
import org.ofbiz.entity.jdbc.ConnectionFactory;
import org.ofbiz.entityext.data.EntityDataLoadContainer;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.tenant.jdbc.TenantConnectionFactory;
import org.ofbiz.tenant.jdbc.TenantJdbcConnectionHandler;

/**
 * Tenant Services
 * @author chatree
 *
 */
public class TenantServices {

    public final static String module = TenantServices.class.getName();
    
    /**
     * install tenant data source
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> installTenantDataSource(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        String tenantId = (String) context.get("tenantId");
        String reader = (String) context.get("reader");
        String entityGroupName = (String) context.get("entityGroupName");

        try {
            // fist make sure if connection handlers are exist
            TenantJdbcConnectionHandler connectionHandler = TenantConnectionFactory.getTenantJdbcConnectionHandler(tenantId, entityGroupName, delegator);
        
            String delegatorName = delegator.getDelegatorBaseName() + "#" + tenantId;
            String[] args = new String[3];
            args[0] = "-reader=" + reader;
            args[1] = "-delegator=" + delegatorName;
            args[2] = "-group=" + entityGroupName;
            String configFile = FileUtil.getFile("component://base/config/install-containers.xml").getAbsolutePath();
            
            // load data
            EntityDataLoadContainer entityDataLoadContainer = new EntityDataLoadContainer();
            entityDataLoadContainer.init(args, configFile);
            entityDataLoadContainer.start();
            
            // close connections
            Delegator tenantDelegator = DelegatorFactory.getDelegator(delegatorName);
            GenericHelperInfo helperInfo = tenantDelegator.getGroupHelperInfo(entityGroupName);
            Connection connection = ConnectionFactory.getConnection(helperInfo);
            connection.close();
        } catch (Exception e) {
            String errMsg = "Could not install a database for tenant " + tenantId + " with entity group name : " + entityGroupName + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        
        return ServiceUtil.returnSuccess();
    }
    
    /**
     * install tenant data sources
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> installTenantDataSources(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String tenantId = (String) context.get("tenantId");
        String reader = (String) context.get("reader");
        
        try {
            List<GenericValue> tenantDataSources = delegator.findByAnd("TenantDataSource", UtilMisc.toMap("tenantId", tenantId));
            for (GenericValue tenantDataSource : tenantDataSources) {
                String entityGroupName = tenantDataSource.getString("entityGroupName");
                
                // install tenant data source
                Map<String, Object> installTenantDataSourceInMap = FastMap.newInstance();
                installTenantDataSourceInMap.put("tenantId", tenantId);
                installTenantDataSourceInMap.put("entityGroupName", entityGroupName);
                installTenantDataSourceInMap.put("reader", reader);
                installTenantDataSourceInMap.put("userLogin", userLogin);
                Map<String, Object> results = dispatcher.runSync("installTenantDataSource", installTenantDataSourceInMap);
                if (ServiceUtil.isError(results)) {
                    List<String> errorMessageList = UtilGenerics.cast(results.get("errorMessageList"));
                    return ServiceUtil.returnError(errorMessageList);
                }
            }
        } catch (Exception e) {
            String errMsg = "Could not install databases for tenant " + tenantId + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        return ServiceUtil.returnSuccess();
    }
    
    /**
     * delete tenant data source database
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> deleteTenantDataSourceDb(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        String tenantId = (String) context.get("tenantId");
        String entityGroupName = (String) context.get("entityGroupName");
        
        try {
            TenantJdbcConnectionHandler connectionHandler = TenantConnectionFactory.getTenantJdbcConnectionHandler(tenantId, entityGroupName, delegator);
            String databaseName = connectionHandler.getDatabaseName();
            connectionHandler.deleteDatabase(databaseName);
        } catch (Exception e) {
            String errMsg = "Could not delete a database for tenant " + tenantId + " with entity group name : " + entityGroupName + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        return ServiceUtil.returnSuccess();
    }
    
    /**
     * delete tenant data source databases
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> deleteTenantDataSourceDbs(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String tenantId = (String) context.get("tenantId");
        
        try {
            List<GenericValue> tenantDataSources = delegator.findByAnd("TenantDataSource", UtilMisc.toMap("tenantId", tenantId));
            for (GenericValue tenantDataSource : tenantDataSources) {
                String entityGroupName = tenantDataSource.getString("entityGroupName");
                
                // delete tenant data source
                Map<String, Object> installTenantDataSourceInMap = FastMap.newInstance();
                installTenantDataSourceInMap.put("tenantId", tenantId);
                installTenantDataSourceInMap.put("entityGroupName", entityGroupName);
                installTenantDataSourceInMap.put("userLogin", userLogin);
                Map<String, Object> results = dispatcher.runSync("deleteTenantDataSource", installTenantDataSourceInMap);
                if (ServiceUtil.isError(results)) {
                    List<String> errorMessageList = UtilGenerics.cast(results.get("errorMessageList"));
                    return ServiceUtil.returnError(errorMessageList);
                }
            }
            
            List<String> errMsgs = FastList.newInstance();
            if (UtilValidate.isNotEmpty(errMsgs)) {
                return ServiceUtil.returnError(errMsgs);
            }
        } catch (Exception e) {
            String errMsg = "Could not delete databases for tenant " + tenantId + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        return ServiceUtil.returnSuccess();
    }
}
