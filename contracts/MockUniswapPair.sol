// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title MockUniswapPair
 * @dev 模拟 Uniswap V2 Pair 的 getReserves()，用于验证 DefiPriceSource 读价逻辑。
 *      reserve0/reserve1 由 owner 设置，模拟 DEX 流动性池储备。
 */
contract MockUniswapPair {
    uint112 public reserve0;
    uint112 public reserve1;
    uint32 private blockTimestampLast;

    event Sync(uint112 reserve0, uint112 reserve1);

    constructor(uint112 _reserve0, uint112 _reserve1) {
        reserve0 = _reserve0;
        reserve1 = _reserve1;
        blockTimestampLast = uint32(block.timestamp);
    }

    /// @dev Uniswap V2 标准接口：返回 (reserve0, reserve1, blockTimestampLast)
    function getReserves() external view returns (uint112 _reserve0, uint112 _reserve1, uint32 _blockTimestampLast) {
        _reserve0 = reserve0;
        _reserve1 = reserve1;
        _blockTimestampLast = blockTimestampLast;
    }

    /// @dev 调整储备（模拟价格变动），owner 可调用
    function setReserves(uint112 _r0, uint112 _r1) external {
        reserve0 = _r0;
        reserve1 = _r1;
        blockTimestampLast = uint32(block.timestamp);
        emit Sync(reserve0, reserve1);
    }
}
