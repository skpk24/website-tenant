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

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TimeZone;

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
import org.ofbiz.entity.condition.EntityComparisonOperator;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityFunction;
import org.ofbiz.entity.condition.EntityJoinOperator;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entityext.data.EntityDataLoadContainer;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericDispatcher;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.tenant.jdbc.TenantConnectionFactory;
import org.ofbiz.tenant.jdbc.TenantJdbcConnectionHandler;
import org.ofbiz.tenant.tenant.TenantWorker;

/**
 * Tenant Services
 * @author chatree
 *
 */
public class TenantServices {

    public final static String module = TenantServices.class.getName();
    
    /**
     * install tenants
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> installTenants(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        
        try {
            List<EntityCondition> conds = FastList.newInstance();
            conds.add(EntityCondition.makeCondition(EntityJoinOperator.OR
                    , EntityCondition.makeCondition("disabled", null)
                    , EntityCondition.makeCondition(EntityFunction.UPPER("disabled"), EntityComparisonOperator.NOT_EQUAL, "N")));
            List<GenericValue> tenants = delegator.findList("Tenant", null, null, null, null, false);
            for (GenericValue tenant : tenants) {
                String tenantId = tenant.getString("tenantId");
                Map<String, Object> installTenantDataSourcesInMap = FastMap.newInstance();
                installTenantDataSourcesInMap.put("tenantId", tenantId);
                Map<String, Object> results = dispatcher.runSync("installTenantDataSources", installTenantDataSourcesInMap);
                if (ServiceUtil.isError(results)) {
                    return ServiceUtil.returnError("Could not install tenant data sources");
                }
            }
            return ServiceUtil.returnSuccess();
        } catch (Exception e) {
            return ServiceUtil.returnError(e.getMessage());
        }
    }
    
    /**
     * install tenant data sources
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> installTenantDataSources(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        String tenantId = (String) context.get("tenantId");
        String readers = (String) context.get("readers");
        
        try {
        
            if (UtilValidate.isEmpty(readers)) {
                // get a reader from file
                List<GenericValue> tenantComponents = delegator.findByAnd("TenantComponent", UtilMisc.toMap("tenantId", tenantId));
                if (UtilValidate.isNotEmpty(tenantComponents)) {
                    GenericValue tenantComponent = EntityUtil.getFirst(tenantComponents);
                    String componentName = tenantComponent.getString("componentName");
                    String demoLoadDataPath = "component://" + componentName + "/data/DemoLoadData.txt";
                    try {
                        File demoLoadDataFile = FileUtil.getFile(demoLoadDataPath);
                        Scanner scanner = new Scanner(demoLoadDataFile);
                        while (scanner.hasNext()) {
                            readers = scanner.nextLine();
                            break;
                        }
                    } catch (Exception e) {
                        Debug.logWarning(e, "Could not read the " + demoLoadDataPath + " file", module);
                    }
                }
            }
            
            // if the reader exists then install data
            if (UtilValidate.isNotEmpty(readers)) {
                if (TransactionUtil.getStatus() == TransactionUtil.STATUS_ACTIVE) {
                    TransactionUtil.commit();
                }
                
                // load data
                String configFile = FileUtil.getFile("component://base/config/install-containers.xml").getAbsolutePath();
                String delegatorName = delegator.getDelegatorBaseName() + "#" + tenantId;
                String[] args = new String[2];
                args[0] = "-readers=" + readers;
                args[1] = "-delegator=" + delegatorName;
                EntityDataLoadContainer entityDataLoadContainer = new EntityDataLoadContainer();
                entityDataLoadContainer.init(args, configFile);
                entityDataLoadContainer.start();
            }
        } catch (Exception e) {
            String errMsg = "Could not install databases for tenant " + tenantId + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        return ServiceUtil.returnSuccess();
    }

    /**
     * create tenant data source database
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> createTenantDataSourceDb(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        String tenantId = (String) context.get("tenantId");
        String entityGroupName = (String) context.get("entityGroupName");
        try {
            TenantJdbcConnectionHandler connectionHandler = TenantConnectionFactory.getTenantJdbcConnectionHandler(tenantId, entityGroupName, delegator);
            return ServiceUtil.returnSuccess();
        } catch (Exception e) {
            String errMsg = "Could not install databases for tenant " + tenantId + " : " + e.getMessage();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
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

    /**
     * create user login for tenant
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> createUserLoginForTenant(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        String tenantId = (String) context.get("tenantId");
        
        Map<String, Object> toContext = FastMap.newInstance();
        
        // set createUserLogin service fields
        String serviceName = "createUserLogin";
        Map<String, Object> setServiceFieldsResults = TenantWorker.setServiceFields(serviceName, context, toContext, timeZone, locale, dispatcher);
        
        if (!ServiceUtil.isError(setServiceFieldsResults)) {
            // run createUserLogin service
            return TenantWorker.runService(serviceName, toContext, true, tenantId, delegator, dispatcher);
        } else {
            return setServiceFieldsResults;
        }
    }

    /**
     * add user login to security group for tenant
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> addUserLoginToSecurityGroupForTenant(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        String tenantId = (String) context.get("tenantId");
        
        Map<String, Object> toContext = FastMap.newInstance();
        
        // set addUserLoginToSecurityGroup service fields
        String serviceName = "addUserLoginToSecurityGroup";
        Map<String, Object> setServiceFieldsResults = TenantWorker.setServiceFields(serviceName, context, toContext, timeZone, locale, dispatcher);
        
        if (!ServiceUtil.isError(setServiceFieldsResults)) {
            // run addUserLoginToSecurityGroup service
            return TenantWorker.runService(serviceName, toContext, true, tenantId, delegator, dispatcher);
        } else {
            return setServiceFieldsResults;
        }
    }

    /**
     * remove user login to security group for tenant
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> removeUserLoginFromSecurityGroupForTenant(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        String tenantId = (String) context.get("tenantId");
        
        Map<String, Object> toContext = FastMap.newInstance();
        
        // set removeUserLoginFromSecurityGroup service fields
        String serviceName = "removeUserLoginFromSecurityGroup";
        Map<String, Object> setServiceFieldsResults = TenantWorker.setServiceFields(serviceName, context, toContext, timeZone, locale, dispatcher);
        
        if (!ServiceUtil.isError(setServiceFieldsResults)) {
            // run removeUserLoginFromSecurityGroup service
            return TenantWorker.runService(serviceName, toContext, true, tenantId, delegator, dispatcher);
        } else {
            return setServiceFieldsResults;
        }
    }

    /**
     * update user login to security group for tenant
     * @param ctx
     * @param context
     * @return
     */
    public static Map<String, Object> updateUserLoginToSecurityGroupForTenant(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        TimeZone timeZone = (TimeZone) context.get("timeZone");
        String tenantId = (String) context.get("tenantId");
        
        Map<String, Object> toContext = FastMap.newInstance();
        
        // set updateUserLoginToSecurityGroup service fields
        String serviceName = "updateUserLoginToSecurityGroup";
        Map<String, Object> setServiceFieldsResults = TenantWorker.setServiceFields(serviceName, context, toContext, timeZone, locale, dispatcher);
        
        if (!ServiceUtil.isError(setServiceFieldsResults)) {
            // run updateUserLoginToSecurityGroup service
            return TenantWorker.runService(serviceName, toContext, true, tenantId, delegator, dispatcher);
        } else {
            return setServiceFieldsResults;
        }
    }
}
