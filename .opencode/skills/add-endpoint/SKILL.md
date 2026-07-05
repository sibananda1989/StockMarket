---
name: add-endpoint
description: Use when adding a new REST API endpoint — creating the controller method, DTO request/response, service method, and wiring everything together. Front-load keywords: add endpoint, new endpoint, rest api, controller, api route, new api.
---

# Add a REST API Endpoint

## Step 1: Identify the right controller (or create one)

Existing controllers in `src/main/java/org/example/controller/`:

| Controller | Base Path | Purpose |
|------------|-----------|---------|
| StockController | `/api/stocks` | Stock CRUD, search, CSV import |
| PriceController | `/api/prices` | Daily price entry and history |
| RsiController | `/api/rsi` | RSI-14 calculation and history |
| TechnicalIndicatorController | `/api/indicators` | All 21 technical indicators |
| SignalController | `/api/signals` | Buy/sell/hold signal generation |
| PortfolioController | `/api/portfolios` | Multi-portfolio CRUD and holdings |
| PortfolioManagementController | `/api/portfolio` | Portfolio history, backfill, screener |
| WatchlistController | `/api/watchlists` | Watchlist CRUD and items |
| SupportResistanceController | `/api/support-resistance` | S/R levels |
| BacktestController | `/api/backtest` | Strategy backtesting |
| DhanController | `/api/dhan` | Dhan broker integration |
| FiiDiiController | `/api/fiidii` | FII/DII institutional flows |
| CorporateEventController | `/api/events` | Corporate event calendar |
| FundamentalDataController | `/api/fundamentals` | Stock fundamentals |
| InstitutionalHoldingController | `/api/institutional` | Holdings, deals, scores, screeners |
| StockHistoryController | `/api/stocks/history` | Yahoo Finance history |
| StockSyncController | `/api/stocks` | Data sync operations |

## Step 2: Create DTO if needed

DTOs live in `src/main/java/org/example/dto/`. `ApiResponse<T>` is also in the `dto` package (`org.example.dto.ApiResponse`) — import it explicitly:

```java
import org.example.dto.ApiResponse;
import org.example.dto.YourRequestDTO;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class YourRequestDTO {
    @NotNull
    private String fieldName;
}
```

Return responses as:
```java
return ResponseEntity.ok(ApiResponse.success(data));
// or
return ResponseEntity.ok(ApiResponse.error("error message"));
```

## Step 3: Add controller method

Follow the existing pattern:

```java
@RestController
@RequestMapping("/api/resource")
public class YourController {
    private final YourService yourService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<YourDTO>>> getAll() {
        List<YourDTO> data = yourService.getAll();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<YourDTO>> create(@Valid @RequestBody YourRequestDTO request) {
        YourDTO data = yourService.create(request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
```

## Step 4: Add service method

Services live in `src/main/java/org/example/service/`. Inject repositories and add business logic:

```java
@Service
@RequiredArgsConstructor
public class YourService {
    private final EntityRepository repository;

    public YourDTO getAll() {
        return repository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
}
```

## Step 5: Add repository method if needed

Repositories extend `JpaRepository` in `src/main/java/org/example/repository/`:

```java
public interface YourRepository extends JpaRepository<Entity, Long> {
    List<Entity> findByStockId(Long stockId);
    @Query("SELECT e FROM Entity e WHERE e.field = :value")
    Optional<Entity> findByCustomQuery(@Param("value") String value);
}
```

## Step 6: Add caching (if needed)

Caches are configured in `CacheConfig.java` at `src/main/java/org/example/CacheConfig.java`. Add `@Cacheable` / `@CacheEvict` on service methods:

```java
@Cacheable(value = "yourCache", key = "#stockId", unless = "#result == null")
public YourDTO getByStockId(Long stockId) { ... }
```

Existing caches (defined in `CacheConfig.java`): `latestIndicators`, `indicatorHistory`, `stockHistory`, `signals`, `supportResistanceLevels`, `institutionalScores`. **Note:** `@Cacheable` only takes effect after app restart (Spring needs to rebuild the proxy).

## Step 7: Add validation and error handling

- Use `@Valid` on request bodies
- Throw custom exceptions from `exception/` package:
  - `StockNotFoundException` → 404
  - `PriceAlreadyExistsException` → 409
  - `InvalidPriceException` → 400
  - `ResourceNotFoundException` → 404
- `GlobalExceptionHandler` handles consistent error JSON responses

## Step 8: Write tests

- Service tests in `src/test/java/org/example/` using JUnit 5 + Mockito
- Controller integration tests using `@SpringBootTest` + `TestRestTemplate` or `MockMvc`
- Follow existing test patterns

## Verification

- Start the app: `mvn spring-boot:run`
- Test the endpoint: `curl http://localhost:8080/api/resource`
- Run tests: `mvn test`
- Check OpenAPI docs at `/swagger-ui.html` (if springdoc-openapi is configured)
