# MidPoint 配置对象的 CI/CD 持续部署实践

## 概述

你的目标是：将 MidPoint 的配置对象（Resource、Role、Object Template、System Configuration 等）以 XML 文件形式存储在 GitLab 中，通过 CI/CD Pipeline 自动部署到 SIT / UAT / PROD 等多个环境的 MidPoint 实例。

这是完全可行的，而且是 MidPoint 官方推荐的最佳实践。

---

## 整体架构

```
GitLab Repository
├── objects/
│   ├── resources/
│   │   ├── ldap-resource.xml
│   │   └── hr-resource.xml
│   ├── roles/
│   │   ├── role-employee.xml
│   │   └── role-contractor.xml
│   ├── object-templates/
│   │   └── user-template.xml
│   └── system-configuration/
│       └── system-configuration.xml
├── environments/
│   ├── sit.properties
│   ├── uat.properties
│   └── prod.properties
└── .gitlab-ci.yml
```

---

## 核心机制

### 1. MidPoint Studio 的环境属性文件（Environment Properties）

MidPoint Studio 支持 `.properties` 文件来做环境变量替换。你在 XML 配置中使用占位符，不同环境使用不同的 properties 文件。

例如 XML 中写：

```xml
<connectorConfiguration>
    <configurationProperties>
        <host>$(ldapHost)</host>
        <port>$(ldapPort)</port>
        <bindDn>$(ldapBindDn)</bindDn>
        <bindPassword>
            <t:clearValue>$(ldapBindPassword)</t:clearValue>
        </bindPassword>
    </configurationProperties>
</connectorConfiguration>
```

`sit.properties`:
```properties
ldapHost=ldap-sit.example.com
ldapPort=389
ldapBindDn=cn=admin,dc=sit,dc=example,dc=com
ldapBindPassword=sit-password-123
```

`prod.properties`:
```properties
ldapHost=ldap.example.com
ldapPort=636
ldapBindDn=cn=midpoint,dc=example,dc=com
ldapBindPassword=prod-secure-password
```

> **注意**：MidPoint Studio 的 `$()` 占位符语法是 Studio 自己在上传前做替换的，MidPoint 服务端本身不认识这个语法。所以在 CI/CD 中你需要自己做这个替换。

### 2. MidPoint Secrets Providers（推荐用于密码/密钥）

从 MidPoint 4.8 开始，推荐使用 Secrets Providers 来管理敏感信息，而不是在 XML 中写明文密码。

```xml
<ldap:password>
    <value>
        <t:externalData>
            <t:provider>env-provider</t:provider>
            <t:key>LDAP_BIND_PASSWORD</t:key>
        </t:externalData>
    </value>
</ldap:password>
```

在 System Configuration 中配置 provider：

```xml
<secretsProviders>
    <environmentVariables>
        <identifier>env-provider</identifier>
        <allowKeyPrefix>MP_</allowKeyPrefix>
    </environmentVariables>
</secretsProviders>
```

这样密码由 MidPoint 运行时从环境变量读取，XML 文件中不含任何明文密码，可以安全地存入 Git。

---

## 部署方式（三选一）

### 方式一：REST API（推荐用于 CI/CD）

MidPoint 提供完整的 REST API，可以通过 HTTP 请求创建/更新配置对象。这是 CI/CD 最常用的方式。

```bash
# 上传/覆盖一个对象（PUT = 覆盖已有对象）
curl -X POST \
  "https://midpoint-sit.example.com/midpoint/ws/rest/roles" \
  -H "Content-Type: application/xml" \
  -H "Authorization: Basic $(echo -n 'administrator:password' | base64)" \
  -d @objects/roles/role-employee.xml

# 如果对象已存在，使用 PUT 更新（需要知道 OID）
curl -X PUT \
  "https://midpoint-sit.example.com/midpoint/ws/rest/roles/aaa10967-ca0f-11e3-bb29-002f9d717e5b" \
  -H "Content-Type: application/xml" \
  -H "Authorization: Basic $(echo -n 'administrator:password' | base64)" \
  -d @objects/roles/role-employee.xml
```

REST API 支持 XML、JSON、YAML 格式。

### 方式二：Ninja 命令行工具（适合直接访问数据库的场景）

Ninja 是 MidPoint 自带的命令行工具，可以直接操作数据库层导入/导出对象。

```bash
# 导入对象，覆盖已有
./bin/ninja.sh import -O -i /path/to/objects/roles/role-employee.xml

# 批量导入整个目录
./bin/ninja.sh import -O -i /path/to/objects/ -l 4

# 导出当前配置（用于备份或初始化 Git 仓库）
./bin/ninja.sh export -O -o backup.xml -r -l 4
```

Ninja 直接操作数据库，不经过 MidPoint 的授权和审计层，适合维护场景但不太适合常规 CI/CD。

### 方式三：Post-Initial Import（适合容器化部署）

将 XML 文件放到 `$MIDPOINT_HOME/post-initial-objects/` 目录下，MidPoint 启动时会自动导入。

```
$MIDPOINT_HOME/post-initial-objects/
├── roles/
│   ├── 010-role-employee.xml
│   └── 020-role-contractor.xml
├── resources/
│   └── 030-ldap-resource.xml
└── templates/
    └── 040-user-template.xml
```

- 文件名以三位数字开头，决定导入顺序
- 导入后文件会被加上 `.done` 后缀，下次启动不会重复导入
- 如果对象已存在则会被覆盖

这种方式特别适合 Docker/Kubernetes 部署，把配置文件通过 ConfigMap 或 Volume 挂载进去。

---

## 推荐的 CI/CD Pipeline 设计

### GitLab CI 示例（`.gitlab-ci.yml`）

```yaml
stages:
  - validate
  - deploy-sit
  - deploy-uat
  - deploy-prod

variables:
  MIDPOINT_SIT_URL: "https://midpoint-sit.example.com/midpoint"
  MIDPOINT_UAT_URL: "https://midpoint-uat.example.com/midpoint"
  MIDPOINT_PROD_URL: "https://midpoint-prod.example.com/midpoint"

# 验证 XML 格式是否正确
validate:
  stage: validate
  script:
    - |
      for f in $(find objects/ -name "*.xml"); do
        xmllint --noout "$f" || exit 1
      done
  only:
    - merge_requests
    - main

# 替换占位符并部署的通用脚本
.deploy_template: &deploy_template
  script:
    - |
      # 替换 MidPoint Studio 风格的占位符 $(key) 为 properties 文件中的值
      deploy_objects() {
        local env_file="$1"
        local midpoint_url="$2"
        local mp_user="$3"
        local mp_pass="$4"

        for xml_file in $(find objects/ -name "*.xml" | sort); do
          echo "Processing: $xml_file"

          # 复制原始文件
          cp "$xml_file" /tmp/deploy_object.xml

          # 替换占位符
          while IFS='=' read -r key value; do
            [[ "$key" =~ ^#.*$ ]] && continue
            [[ -z "$key" ]] && continue
            sed -i "s|\$(${key})|${value}|g" /tmp/deploy_object.xml
          done < "$env_file"

          # 通过 REST API 上传（使用 POST + overwrite 选项）
          HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" \
            -X POST \
            "${midpoint_url}/ws/rest/rpc/executeScript" \
            -H "Content-Type: application/xml" \
            -u "${mp_user}:${mp_pass}" \
            -d "<s:executeScript xmlns:s='http://midpoint.evolveum.com/xml/ns/public/model/scripting-3'
                  xmlns:c='http://midpoint.evolveum.com/xml/ns/public/common/common-3'>
                  <s:action>
                    <s:type>execute-script</s:type>
                  </s:action>
                </s:executeScript>")

          # 或者更简单地直接 POST 对象
          # 需要根据对象类型选择正确的 endpoint
          echo "Response: $HTTP_CODE"
        done
      }

deploy-sit:
  stage: deploy-sit
  <<: *deploy_template
  variables:
    ENV_FILE: environments/sit.properties
  script:
    - deploy_to_midpoint "$ENV_FILE" "$MIDPOINT_SIT_URL" "$SIT_MP_USER" "$SIT_MP_PASS"
  only:
    - main
  environment:
    name: sit

deploy-uat:
  stage: deploy-uat
  <<: *deploy_template
  variables:
    ENV_FILE: environments/uat.properties
  script:
    - deploy_to_midpoint "$ENV_FILE" "$MIDPOINT_UAT_URL" "$UAT_MP_USER" "$UAT_MP_PASS"
  only:
    - main
  when: manual
  environment:
    name: uat

deploy-prod:
  stage: deploy-prod
  <<: *deploy_template
  variables:
    ENV_FILE: environments/prod.properties
  script:
    - deploy_to_midpoint "$ENV_FILE" "$MIDPOINT_PROD_URL" "$PROD_MP_USER" "$PROD_MP_PASS"
  only:
    - tags
  when: manual
  environment:
    name: production
```

---

## 更实用的部署脚本（推荐）

与其自己写复杂的 REST 调用逻辑，更推荐写一个简单的部署脚本：

```bash
#!/bin/bash
# deploy.sh - 部署 MidPoint 配置对象
# 用法: ./deploy.sh <env_properties_file> <midpoint_url> <username> <password>

ENV_FILE=$1
MP_URL=$2
MP_USER=$3
MP_PASS=$4

# 对象类型到 REST endpoint 的映射
get_endpoint() {
  local file=$1
  if grep -q "<resource " "$file"; then echo "resources"
  elif grep -q "<role " "$file"; then echo "roles"
  elif grep -q "<org " "$file"; then echo "orgs"
  elif grep -q "<objectTemplate " "$file"; then echo "objectTemplates"
  elif grep -q "<systemConfiguration " "$file"; then echo "systemConfigurations"
  elif grep -q "<archetype " "$file"; then echo "archetypes"
  elif grep -q "<task " "$file"; then echo "tasks"
  elif grep -q "<securityPolicy " "$file"; then echo "securityPolicies"
  elif grep -q "<valuePolicy " "$file"; then echo "valuePolicies"
  elif grep -q "<service " "$file"; then echo "services"
  else echo "unknown"
  fi
}

# 从 XML 中提取 OID
get_oid() {
  grep -oP 'oid="[^"]*"' "$1" | head -1 | grep -oP '"[^"]*"' | tr -d '"'
}

# 替换占位符
substitute_properties() {
  local src=$1
  local dest=$2
  cp "$src" "$dest"
  while IFS='=' read -r key value; do
    [[ "$key" =~ ^[[:space:]]*#.*$ ]] && continue
    [[ -z "$key" ]] && continue
    key=$(echo "$key" | xargs)
    value=$(echo "$value" | xargs)
    sed -i "s|\$(${key})|${value}|g" "$dest"
  done < "$ENV_FILE"
}

# 部署单个对象
deploy_object() {
  local xml_file=$1
  local tmp_file="/tmp/mp_deploy_$(basename $xml_file)"

  substitute_properties "$xml_file" "$tmp_file"

  local endpoint=$(get_endpoint "$tmp_file")
  local oid=$(get_oid "$tmp_file")

  if [ "$endpoint" = "unknown" ]; then
    echo "SKIP: 无法识别对象类型 - $xml_file"
    return
  fi

  echo "部署: $xml_file -> $endpoint/$oid"

  # 尝试 PUT（更新已有对象）
  HTTP_CODE=$(curl -s -o /tmp/mp_response.txt -w "%{http_code}" \
    -X PUT \
    "${MP_URL}/ws/rest/${endpoint}/${oid}" \
    -H "Content-Type: application/xml" \
    -u "${MP_USER}:${MP_PASS}" \
    -d @"$tmp_file")

  if [ "$HTTP_CODE" = "404" ]; then
    # 对象不存在，用 POST 创建
    HTTP_CODE=$(curl -s -o /tmp/mp_response.txt -w "%{http_code}" \
      -X POST \
      "${MP_URL}/ws/rest/${endpoint}" \
      -H "Content-Type: application/xml" \
      -u "${MP_USER}:${MP_PASS}" \
      -d @"$tmp_file")
  fi

  if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
    echo "  成功 (HTTP $HTTP_CODE)"
  else
    echo "  失败 (HTTP $HTTP_CODE)"
    cat /tmp/mp_response.txt
    exit 1
  fi

  rm -f "$tmp_file"
}

# 按文件名排序部署所有对象
for xml_file in $(find objects/ -name "*.xml" | sort); do
  deploy_object "$xml_file"
done

echo "部署完成！"
```

---

## 关键最佳实践

### 1. 固定 OID

每个配置对象必须有固定的 OID。这样才能在不同环境间保持一致，也才能做到幂等部署（重复部署不会创建重复对象）。

```xml
<role oid="aaa10967-ca0f-11e3-bb29-002f9d717e5b">
    <name>Employee</name>
    ...
</role>
```

用 `uuid` 命令生成：
```bash
uuid
# 输出: b26554d2-41fc-11e5-a652-3c970e44b9e2
```

### 2. 敏感信息处理

不要在 Git 中存储明文密码。有三种方式：

- **Secrets Providers**（推荐）：XML 中引用 provider key，密码由 MidPoint 运行时从环境变量/Docker Secret/文件读取
- **CI/CD 变量**：密码存在 GitLab CI/CD Variables 中，部署时替换
- **properties 文件中的密码**：properties 文件不入 Git，只在 CI/CD 中作为 Secret File 注入

### 3. 部署顺序

配置对象之间有依赖关系，部署顺序很重要：

1. System Configuration
2. Security Policy / Value Policy
3. Archetypes
4. Object Templates
5. Resources（依赖 connector）
6. Roles（可能引用 Resource OID）
7. Orgs
8. Tasks

用文件名前缀控制顺序：
```
objects/
├── 010-system-configuration.xml
├── 020-security-policy.xml
├── 030-archetype-employee.xml
├── 040-user-template.xml
├── 050-ldap-resource.xml
├── 060-role-employee.xml
├── 070-org-structure.xml
└── 080-recon-task.xml
```

### 4. 使用 Simulation（MidPoint 4.7+）

MidPoint 4.7 引入了 Simulation 功能，可以在不实际执行的情况下预览变更效果。建议在 UAT/PROD 部署前先跑 simulation。

### 5. 版本控制策略

- `main` 分支 → 自动部署到 SIT
- 手动触发 → 部署到 UAT
- 打 Tag → 手动部署到 PROD
- 使用 Merge Request 做 Code Review

---

## Docker/Kubernetes 场景

如果 MidPoint 运行在容器中，推荐使用 post-initial-objects 机制：

```yaml
# docker-compose.yml 示例
services:
  midpoint:
    image: evolveum/midpoint:latest
    volumes:
      - ./post-initial-objects:/opt/midpoint/var/post-initial-objects
    environment:
      - MP_LDAP_HOST=ldap.example.com
      - MP_LDAP_PASSWORD=secret
```

```yaml
# Kubernetes ConfigMap 示例
apiVersion: v1
kind: ConfigMap
metadata:
  name: midpoint-config-objects
data:
  050-ldap-resource.xml: |
    <resource oid="...">
      ...
    </resource>
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: midpoint
          volumeMounts:
            - name: config-objects
              mountPath: /opt/midpoint/var/post-initial-objects
      volumes:
        - name: config-objects
          configMap:
            name: midpoint-config-objects
```

---

## 总结对比

| 方式 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| REST API | CI/CD Pipeline | 标准化、有审计、支持授权 | 需要 MidPoint 运行中 |
| Ninja | 维护/迁移 | 直接操作 DB、不需要 MidPoint 运行 | 绕过授权和审计 |
| Post-Initial Import | 容器化部署 | 简单、启动时自动导入 | 只在启动时执行一次 |
| midpoint-client-java | Java 应用集成 | 类型安全、Fluent API | 需要 Java 环境 |

**推荐组合**：Git 存储 XML + properties 文件做环境差异 + Secrets Providers 管理密码 + REST API 做 CI/CD 部署。
