// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title SimpleAMM
 * @dev 简化版 AMM 做市合约（Uniswap V2 恒定乘积式，演示 DeFi 核心逻辑）。
 *
 * 核心公式：x * y = k（恒定乘积）
 *  - addLiquidity: 按当前价格比例注入两种代币，铸造 LP 份额
 *  - removeLiquidity: 按 LP 份额按比例提取两种代币
 *  - swap: 用单边代币兑换另一侧，遵循 x*y=k，收取 0.3% 手续费（简化按 0 计）
 *
 * 合约只管理两种 ERC-20 代币的储备（reserve），不发行 LP 代币（简化，LP 用持仓比例表示）。
 */
interface IERC20 {
    function transferFrom(address from, address to, uint256 value) external returns (bool);
    function transfer(address to, uint256 value) external returns (bool);
    function balanceOf(address account) external view returns (uint256);
}

contract SimpleAMM {
    IERC20 public token0;
    IERC20 public token1;
    uint256 public reserve0;
    uint256 public reserve1;
    uint256 public totalLiquidity;
    mapping(address => uint256) public liquidity;

    event LiquidityAdded(address indexed provider, uint256 amount0, uint256 amount1, uint256 lp);
    event LiquidityRemoved(address indexed provider, uint256 lp, uint256 amount0, uint256 amount1);
    event Swap(address indexed trader, address indexed tokenIn, uint256 amountIn, uint256 amountOut);

    constructor(address _token0, address _token1) {
        token0 = IERC20(_token0);
        token1 = IERC20(_token1);
    }

    /// @dev 添加流动性：按当前储备比例注入两币，铸造 LP 份额
    function addLiquidity(uint256 amount0Desired, uint256 amount1Desired) external returns (uint256 lp) {
        require(amount0Desired > 0 && amount1Desired > 0, "zero amount");
        token0.transferFrom(msg.sender, address(this), amount0Desired);
        token1.transferFrom(msg.sender, address(this), amount1Desired);

        if (totalLiquidity == 0) {
            // 首笔流动性：LP 份额 = 两种代币的几何平均
            lp = _sqrt(amount0Desired * amount1Desired);
        } else {
            // 按比例：lp = min(amount0*L/reserve0, amount1*L/reserve1)
            uint256 lp0 = amount0Desired * totalLiquidity / reserve0;
            uint256 lp1 = amount1Desired * totalLiquidity / reserve1;
            lp = lp0 < lp1 ? lp0 : lp1;
        }
        require(lp > 0, "insufficient liquidity");

        reserve0 = token0.balanceOf(address(this));
        reserve1 = token1.balanceOf(address(this));
        liquidity[msg.sender] += lp;
        totalLiquidity += lp;
        emit LiquidityAdded(msg.sender, amount0Desired, amount1Desired, lp);
    }

    /// @dev 移除流动性：按 LP 份额比例提取两种代币
    function removeLiquidity(uint256 lp) external returns (uint256 amount0, uint256 amount1) {
        require(lp > 0 && lp <= liquidity[msg.sender], "invalid lp");
        amount0 = lp * reserve0 / totalLiquidity;
        amount1 = lp * reserve1 / totalLiquidity;
        liquidity[msg.sender] -= lp;
        totalLiquidity -= lp;
        reserve0 -= amount0;
        reserve1 -= amount1;
        token0.transfer(msg.sender, amount0);
        token1.transfer(msg.sender, amount1);
        emit LiquidityRemoved(msg.sender, lp, amount0, amount1);
    }

    /// @dev 兑换：输入单边代币，按 x*y=k 输出另一侧。amountIn 为已转进合约的数量。
    function swap(address tokenIn, uint256 amountIn) external returns (uint256 amountOut) {
        require(amountIn > 0, "zero amount");
        require(tokenIn == address(token0) || tokenIn == address(token1), "invalid token");

        bool is0 = tokenIn == address(token0);
        uint256 reserveIn = is0 ? reserve0 : reserve1;
        uint256 reserveOut = is0 ? reserve1 : reserve0;

        // 恒定乘积：amountOut = amountIn * reserveOut / (reserveIn + amountIn)
        // 用合约实际余额差计算真实入账（防税币）
        uint256 balanceIn = IERC20(tokenIn).balanceOf(address(this));
        uint256 realIn = balanceIn - (is0 ? reserve0 : reserve1);
        amountOut = realIn * reserveOut / (reserveIn + realIn);
        require(amountOut > 0 && amountOut < reserveOut, "insufficient output");

        if (is0) {
            reserve0 = balanceIn;
            reserve1 = reserveOut - amountOut;
            token1.transfer(msg.sender, amountOut);
        } else {
            reserve1 = balanceIn;
            reserve0 = reserveOut - amountOut;
            token0.transfer(msg.sender, amountOut);
        }
        emit Swap(msg.sender, tokenIn, realIn, amountOut);
    }

    /// @dev 查询价格（输出量）
    function getAmountOut(uint256 amountIn, address tokenIn) public view returns (uint256) {
        bool is0 = tokenIn == address(token0);
        uint256 reserveIn = is0 ? reserve0 : reserve1;
        uint256 reserveOut = is0 ? reserve1 : reserve0;
        return amountIn * reserveOut / (reserveIn + amountIn);
    }

    function _sqrt(uint256 y) internal pure returns (uint256 z) {
        if (y > 3) {
            z = y;
            uint256 x = y / 2 + 1;
            while (x < z) {
                z = x;
                x = (y / x + x) / 2;
            }
        } else if (y != 0) {
            z = 1;
        }
    }
}
